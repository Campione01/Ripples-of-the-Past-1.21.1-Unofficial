package rotp.addons.yellowtemperance.client;

import rotp.core.client.ClientGlobals;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.yellowtemperance.YellowTemperanceAddon;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

final class ErosionVisuals {
    private ErosionVisuals() {}

    @SuppressWarnings({"rawtypes", "unchecked"})
    static void render(
            LivingEntity target,
            EntityModel<?> rawModel,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            float partialTick) {
        MobEffectInstance erosion =
                target.getEffect(YellowTemperanceAddon.EROSION);
        if (erosion == null) {
            ErosionVisualState.apply(
                    target.getId(), 0, false);
            return;
        }
        if (!ClientGlobals.canSeeStands) {
            return;
        }
        YellowTemperanceVisuals.RenderState state =
                YellowTemperanceVisuals.resolveController(
                        ErosionVisualState.controller(target),
                        partialTick);
        StandVisualContext context = state != null
                ? state.context()
                : StandVisualContext.preview();
        float levelAlpha = Math.min(
                0.72F,
                0.30F + erosion.getAmplifier() * 0.07F);
        MultiBufferSource alphaBuffer =
                AlphaMultiBufferSource.wrap(
                        buffer,
                        context.effectiveAlpha()
                                * levelAlpha);
        VertexConsumer vertices = alphaBuffer.getBuffer(
                ModRenderTypes.standTranslucent(
                        YellowTemperanceVisuals.EROSION_TEXTURE,
                        context.classicOutline()));
        EntityModel model = rawModel;
        poseStack.pushPose();
        poseStack.scale(1.012F, 1.012F, 1.012F);
        model.renderToBuffer(
                poseStack,
                vertices,
                packedLight,
                LivingEntityRenderer.getOverlayCoords(
                        target, 0.0F),
                0xFFFFE35B);
        poseStack.popPose();
    }
}
