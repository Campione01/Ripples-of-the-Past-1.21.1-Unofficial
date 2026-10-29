package rotp.addons.thesun.client;

import rotp.core.client.entityrender.stand.StandEntityRenderState;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import rotp.core.compat.v1_21_4.renderstate.RenderStateCrutches;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.thesun.entity.TheSunEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;

public final class TheSunCoronaLayer extends RenderLayer<
        TheSunEntity, TheSunModel> {
    private final TheSunRenderer renderer;

    public TheSunCoronaLayer(
            TheSunRenderer renderer) {
        super(renderer);
        this.renderer = renderer;
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            TheSunEntity entity,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        if (!(RenderStateCrutches.currentStandEntityRenderState
                instanceof StandEntityRenderState renderState)) {
            return;
        }
        float lifecycleAlpha =
                entity.clientStuff.getAlpha(entity, partialTick);
        StandVisualContext context = StandVisualContext.world(
                entity, lifecycleAlpha);
        if (context.effectiveAlpha() <= 0.0F
                || renderState.isInvisibleToPlayer
                        && !renderState.visibleForSpectator) {
            return;
        }

        MultiBufferSource alphaBuffer =
                AlphaMultiBufferSource.wrap(
                        buffer, context.effectiveAlpha());
        VertexConsumer vertex = alphaBuffer.getBuffer(
                ModRenderTypes.standTranslucent(
                        renderer.getTextureLocation(entity),
                        false));
        getParentModel().renderOutlineToBuffer(
                poseStack,
                vertex,
                LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY,
                -1);
    }
}
