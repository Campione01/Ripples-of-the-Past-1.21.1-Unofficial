package rotp.addons.whitesnake.client;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import rotp.core.api.client.render.FirstPersonPostArmLayers;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.whitesnake.WhitesnakeAddon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

public final class MeltHeartOverlayRenderer {
    private static final Map<ResourceLocation, TextureSize>
            TEXTURE_SIZE_CACHE = new HashMap<>();

    private MeltHeartOverlayRenderer() {}

    public static void renderFirstPerson(
            FirstPersonPostArmLayers.Context arm) {
        if (!(arm.entity() instanceof AbstractClientPlayer player)
                || player.isSpectator()) {
            return;
        }
        MobEffectInstance effect = player.getEffect(
                WhitesnakeAddon.MELT_HEART_EFFECT);
        if (effect == null) {
            return;
        }
        VertexConsumer vertices = arm.buffer().getBuffer(
                RenderType.entityTranslucent(texture(
                        TextureSize.DEFAULT,
                        effect.getAmplifier())));
        arm.renderArm(vertices);
    }

    /**
     * The melt overlay on the wearer's own body.
     *
     * <p>This used to run from {@code RenderLivingEvent.Post}, but NeoForge fires that event after
     * {@code LivingEntityRenderer.render} has already popped the pose it set up, so the model was re-rendered outside
     * the entity's transform and never appeared. 1.16 add-ons hung this kind of overlay on the renderer as a layer;
     * a {@link RenderLayer} is the same thing here and runs inside the body transform.
     */
    public static final class Layer<
            T extends LivingEntity, M extends EntityModel<T>>
            extends RenderLayer<T, M> {
        private final RenderLayerParent<T, M> parent;

        public Layer(RenderLayerParent<T, M> parent) {
            super(parent);
            this.parent = parent;
        }

        @Override
        public void render(
                PoseStack poseStack,
                MultiBufferSource buffer,
                int packedLight,
                T entity,
                float limbSwing,
                float limbSwingAmount,
                float partialTick,
                float ageInTicks,
                float netHeadYaw,
                float headPitch) {
            MobEffectInstance effect = entity.getEffect(
                    WhitesnakeAddon.MELT_HEART_EFFECT);
            if (effect == null || entity.isInvisible()) {
                return;
            }
            TextureSize size = TEXTURE_SIZE_CACHE.computeIfAbsent(
                    parent.getTextureLocation(entity),
                    MeltHeartOverlayRenderer::readTextureSize);
            VertexConsumer vertices = buffer.getBuffer(
                    RenderType.entityTranslucent(texture(
                            size, effect.getAmplifier())));
            getParentModel().renderToBuffer(
                    poseStack,
                    vertices,
                    LightTexture.FULL_BRIGHT,
                    LivingEntityRenderer.getOverlayCoords(
                            entity, 0.0F),
                    0xFFFFFFFF);
        }
    }

    private static ResourceLocation texture(
            TextureSize size, int amplifier) {
        int frame = Math.clamp(amplifier + 1, 1, 4);
        return WhitesnakeAddon.id(
                "textures/entity/layer/melt_heart/t"
                        + size.width
                        + "x"
                        + size.height
                        + "/"
                        + frame
                        + ".png");
    }

    private static TextureSize readTextureSize(
            ResourceLocation texture) {
        return Minecraft.getInstance()
                .getResourceManager()
                .getResource(texture)
                .map(MeltHeartOverlayRenderer::readTextureSize)
                .orElse(TextureSize.DEFAULT);
    }

    private static TextureSize readTextureSize(Resource resource) {
        try (InputStream stream = resource.open();
                NativeImage image = NativeImage.read(stream)) {
            int width = closestPowerOfTwo(image.getWidth());
            int height = image.getHeight() <= image.getWidth() / 2
                    ? width / 2
                    : width;
            return new TextureSize(width, height);
        }
        catch (IOException | RuntimeException ignored) {
            return TextureSize.DEFAULT;
        }
    }

    private static int closestPowerOfTwo(int width) {
        if (width <= 64) {
            return 64;
        }
        if (width <= 128) {
            return 128;
        }
        return 256;
    }

    private record TextureSize(int width, int height) {
        private static final TextureSize DEFAULT =
                new TextureSize(64, 64);
    }
}
