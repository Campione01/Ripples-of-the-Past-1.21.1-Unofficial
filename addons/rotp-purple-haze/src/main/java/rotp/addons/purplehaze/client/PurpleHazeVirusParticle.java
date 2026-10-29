package rotp.addons.purplehaze.client;

import java.util.UUID;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import rotp.core.client.util.functions.ClientUtil;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.purplehaze.PurpleHazeAddon;
import rotp.addons.purplehaze.particle.PurpleHazeVirusParticleOptions;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.model.pipeline.VertexConsumerWrapper;

public final class PurpleHazeVirusParticle
        extends SingleQuadParticle {
    private static final ResourceLocation TEXTURE =
            PurpleHazeAddon.id(
                    "textures/particle/purple_haze_virus.png");

    private final int controllerId;
    private final UUID ownerUuid;

    private PurpleHazeVirusParticle(
            PurpleHazeVirusParticleOptions options,
            ClientLevel level,
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed) {
        super(
                level,
                x,
                y,
                z,
                xSpeed,
                ySpeed,
                zSpeed);
        controllerId = options.controllerId();
        ownerUuid = options.ownerUuid();
        lifetime = 100;
        quadSize = 0.05F;
        hasPhysics = false;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.CUSTOM;
    }

    @Override
    public float getQuadSize(float partialTick) {
        float floatAge = age + partialTick;
        float sizeFactor = floatAge < lifetime / 2.0F
                ? floatAge
                : lifetime - floatAge;
        return quadSize * Mth.clamp(
                sizeFactor / (lifetime / 2.0F) * 4.0F,
                0.0F,
                1.0F);
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime) {
            remove();
        }
    }

    @Override
    public void render(
            VertexConsumer ignored,
            Camera camera,
            float partialTick) {
        StandVisualContext context =
                PurpleHazeVisuals.fromControllerId(
                        level,
                        controllerId,
                        ownerUuid,
                        partialTick);
        if (!PurpleHazeVisuals.canRender(context)) {
            return;
        }

        MultiBufferSource.BufferSource root =
                Minecraft.getInstance()
                        .renderBuffers()
                        .bufferSource();
        MultiBufferSource alphaBuffer =
                AlphaMultiBufferSource.wrap(
                        root,
                        context.effectiveAlpha());
        RenderType renderType =
                ModRenderTypes.standTranslucentCull(TEXTURE);
        super.render(
                alphaBuffer.getBuffer(renderType),
                camera,
                partialTick);
        root.endBatch(renderType);
    }

    @Override
    protected void renderRotatedQuad(
            VertexConsumer vertices,
            Quaternionf rotation,
            float x,
            float y,
            float z,
            float partialTick) {
        Vector3f normal = new Vector3f(0.0F, 0.0F, 1.0F)
                .rotate(rotation);
        // The Stand material needs overlay UV and a normal in addition to particle attributes.
        VertexConsumer entityVertices = new VertexConsumerWrapper(vertices) {
            @Override
            public VertexConsumer addVertex(float x, float y, float z) {
                parent.addVertex(x, y, z)
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setNormal(normal.x(), normal.y(), normal.z());
                return this;
            }

            @Override
            public VertexConsumer setColor(
                    float red, float green, float blue, float alpha) {
                parent.setColor(red, green, blue, alpha);
                return this;
            }
        };
        super.renderRotatedQuad(
                entityVertices, rotation, x, y, z, partialTick);
    }

    @Override
    public int getLightColor(float partialTick) {
        return ClientUtil.MAX_LIGHT;
    }

    @Override
    protected float getU0() {
        return 0.0F;
    }

    @Override
    protected float getU1() {
        return 1.0F;
    }

    @Override
    protected float getV0() {
        return 0.0F;
    }

    @Override
    protected float getV1() {
        return 1.0F;
    }

    public static final class Provider
            implements ParticleProvider<
                    PurpleHazeVirusParticleOptions> {
        public Provider(SpriteSet ignored) {}

        @Override
        public Particle createParticle(
                PurpleHazeVirusParticleOptions options,
                ClientLevel level,
                double x,
                double y,
                double z,
                double xSpeed,
                double ySpeed,
                double zSpeed) {
            return new PurpleHazeVirusParticle(
                    options,
                    level,
                    x,
                    y,
                    z,
                    xSpeed,
                    ySpeed,
                    zSpeed);
        }
    }
}
