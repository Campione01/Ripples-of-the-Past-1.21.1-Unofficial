package rotp.addons.whitesnake.client;

import rotp.core.client.rendertype.ModRenderTypes;
import rotp.core.client.rendertype.BarrageVertexConsumer;
import rotp.core.client.shader.ModShaders;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import rotp.addons.whitesnake.WhitesnakeAddon;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

@EventBusSubscriber(modid = WhitesnakeAddon.MOD_ID, value = Dist.CLIENT)
public final class WhitesnakeGlintRenderType extends RenderType {
    private static ShaderInstance privateReplayShader;
    private static final RenderType PRIVATE_GLINT = glint(MAIN_TARGET,
            new ShaderStateShard(() -> privateReplayShader));
    private static final RenderType NATIVE_GLINT = glint(ITEM_ENTITY_TARGET, RENDERTYPE_EYES_SHADER);

    @SubscribeEvent
    public static void registerShader(RegisterShadersEvent event) {
        privateReplayShader = null;
        ModShaders.loadPrivateTargetCoreShader(event,
                ResourceLocation.withDefaultNamespace("rendertype_eyes"),
                DefaultVertexFormat.NEW_ENTITY, shader -> privateReplayShader = shader);
    }

    public static boolean isReady() {
        return privateReplayShader != null;
    }

    public static RenderType forCurrentBody() {
        return ModRenderTypes.standSurfaceOverlay(PRIVATE_GLINT,
                () -> privateReplayShader, NATIVE_GLINT, NATIVE_GLINT);
    }

    private static RenderType glint(RenderStateShard.OutputStateShard target, ShaderStateShard shader) {
        return create(
            "rotp_whitesnake:surface_glint",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            1536,
            false,
            true,
            CompositeState.builder()
                    .setShaderState(shader)
                    .setTextureState(new RenderStateShard.TextureStateShard(
                            WhitesnakeAddon.id("textures/glint.png"), true, false))
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setTransparencyState(GLINT_TRANSPARENCY)
                    .setOutputState(target)
                    .createCompositeState(false));
    }

    private WhitesnakeGlintRenderType(
            String name, VertexFormat format, VertexFormat.Mode mode,
            int bufferSize, boolean affectsCrumbling, boolean sortOnUpload,
            Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload,
                setupState, clearState);
    }

    public static VertexConsumer vertices(
            VertexConsumer delegate, float alpha, long millis, double speed) {
        return WhitesnakeGlintVertices.wrap(delegate,
                WhitesnakeGlintTransform.create(alpha, millis, speed));
    }
}

record WhitesnakeGlintVertices(
        VertexConsumer delegate,
        WhitesnakeGlintTransform transform) implements VertexConsumer {
    static VertexConsumer wrap(VertexConsumer delegate, WhitesnakeGlintTransform transform) {
        VertexConsumer body = new WhitesnakeGlintVertices(delegate, transform);
        if (delegate instanceof BarrageVertexConsumer) {
            return new BarrageVertexConsumer(body, swing -> new WhitesnakeGlintVertices(
                    BarrageVertexConsumer.forBarrage(delegate, swing), transform));
        }
        return body;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        delegate.addVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        delegate.setColor(transform.color(red), transform.color(green),
                transform.color(blue), alpha);
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        delegate.setUv(transform.u(u, v), transform.v(u, v));
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        delegate.setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        delegate.setUv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        delegate.setNormal(x, y, z);
        return this;
    }
}

record WhitesnakeGlintTransform(float offsetU, float offsetV, float strength) {
    private static final float COS = (float) Math.cos(Math.PI / 18.0);
    private static final float SIN = (float) Math.sin(Math.PI / 18.0);
    private static final float SCALE = 0.16F;

    static WhitesnakeGlintTransform create(float alpha, long millis, double speed) {
        long time = (long) (millis * speed * 8.0);
        // SRC_COLOR + ONE squares RGB, so sqrt(alpha) preserves linear fade strength.
        return new WhitesnakeGlintTransform(
                -(float) (time % 110000L) / 110000.0F,
                (float) (time % 30000L) / 30000.0F,
                (float) Math.sqrt(Math.max(0.0F, Math.min(1.0F, alpha))));
    }

    float u(float u, float v) {
        return offsetU + SCALE * (COS * u - SIN * v);
    }

    float v(float u, float v) {
        return offsetV + SCALE * (SIN * u + COS * v);
    }

    int color(int component) {
        return Math.max(0, Math.min(255, Math.round(component * strength)));
    }
}
