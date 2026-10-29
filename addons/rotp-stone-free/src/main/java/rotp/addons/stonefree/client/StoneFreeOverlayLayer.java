package rotp.addons.stonefree.client;

import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import rotp.core.client.standskin.StandSkin;
import rotp.core.client.standskin.StandSkinsLoader;
import rotp.core.powersystem.standpower.StandPower;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import rotp.addons.stonefree.StoneFreeAddon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * The string decomposition and Mobius Strip overlays, drawn over the wearer's own body.
 *
 * <p>This used to run from {@code RenderLivingEvent.Post}, but NeoForge fires that event after
 * {@code LivingEntityRenderer.render} has already popped the pose it set up, so the model was re-rendered outside the
 * entity's transform and never appeared on screen. 1.16 hung these on the renderer as layers
 * ({@code LayerRenderer}, added with {@code func_177094_a}); a {@link RenderLayer} is the same thing here and runs
 * inside the body transform.
 */
public final class StoneFreeOverlayLayer<
        T extends LivingEntity, M extends EntityModel<T>>
        extends RenderLayer<T, M> {
    // 1.16 shipped the string decomposition frames in two layouts and picked one from the rendered model's texture
    // size; both sets are still shipped here.
    private static final String SQUARE_FRAMES = "t64x64/";
    private static final String HALF_FRAMES = "t64x32/";
    private static final Map<ResourceLocation, String> FRAME_SET_CACHE =
            new HashMap<>();

    private final RenderLayerParent<T, M> parent;

    public StoneFreeOverlayLayer(RenderLayerParent<T, M> parent) {
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
        if (entity.isInvisible()) {
            return;
        }
        MobEffectInstance decomposition = entity.getEffect(
                StoneFreeAddon.STRING_DECOMPOSITION);
        boolean mobius = entity.hasEffect(
                StoneFreeAddon.MOBIUS_STRIP);
        if (decomposition == null && !mobius) {
            return;
        }

        StandVisualContext context =
                StoneFreeVisuals.overlayContext(
                        entity, partialTick);
        if (context == null
                || context.effectiveAlpha() <= 0.0F) {
            return;
        }
        if (decomposition != null) {
            int stage = Math.min(
                    8, decomposition.getAmplifier() + 1);
            renderOverlay(
                    poseStack,
                    buffer,
                    packedLight,
                    entity,
                    texture(
                            entity,
                            StoneFreeAddon.id(
                                    "textures/entity/biped/layer/"
                                            + "string_decomposition/"
                                            + frameSet(entity)
                                            + stage
                                            + ".png")),
                    context,
                    1.002F);
        }
        if (mobius) {
            renderOverlay(
                    poseStack,
                    buffer,
                    packedLight,
                    entity,
                    texture(
                            entity,
                            StoneFreeAddon.id(
                                    "textures/entity/biped/layer/"
                                            + "mobius_strip.png")),
                    context,
                    1.004F);
        }
    }

    private void renderOverlay(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            T entity,
            ResourceLocation texture,
            StandVisualContext context,
            float scale) {
        MultiBufferSource alphaBuffer =
                AlphaMultiBufferSource.wrap(
                        buffer, context.effectiveAlpha());
        VertexConsumer vertices = alphaBuffer.getBuffer(
                ModRenderTypes.standTranslucent(
                        texture,
                        context.classicOutline()));
        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
        getParentModel().renderToBuffer(
                poseStack,
                vertices,
                packedLight,
                LivingEntityRenderer.getOverlayCoords(
                        entity, 0.0F),
                0xFFFFFFFF);
        poseStack.popPose();
    }

    /**
     * 1.16's StringDecompositionLayer chose its frame set from the rendered model's texture size, which is why both
     * t64x32 and t64x64 are shipped. A biped drawn from a 64x32 texture (vanilla skeletons, legacy-format skins) needs
     * the half-height set or the overlay lands on the wrong parts. The size is read off the entity's own texture, the
     * way the Heaven's Door book layer does it; a skin the resource manager cannot open (player skins are registered
     * dynamically) is the modern square layout.
     */
    private String frameSet(T entity) {
        return FRAME_SET_CACHE.computeIfAbsent(
                parent.getTextureLocation(entity),
                StoneFreeOverlayLayer::readFrameSet);
    }

    private static String readFrameSet(ResourceLocation texture) {
        return Minecraft.getInstance()
                .getResourceManager()
                .getResource(texture)
                .map(StoneFreeOverlayLayer::readFrameSet)
                .orElse(SQUARE_FRAMES);
    }

    private static String readFrameSet(Resource resource) {
        try (InputStream stream = resource.open();
                NativeImage image = NativeImage.read(stream)) {
            return image.getHeight() * 4 >= image.getWidth() * 3
                    ? SQUARE_FRAMES
                    : HALF_FRAMES;
        }
        catch (IOException | RuntimeException ignored) {
            return SQUARE_FRAMES;
        }
    }

    private static ResourceLocation texture(
            LivingEntity entity,
            ResourceLocation baseTexture) {
        StandPower power = StandPower.get(entity);
        StandSkin skin = power != null
                ? StandSkinsLoader.getInstance().getSkin(power)
                : null;
        return skin != null
                ? skin.getTexture(baseTexture, baseTexture)
                : baseTexture;
    }
}
