package rotp.addons.purplehaze.client;

import rotp.core.client.entityrender.stand.StandEntityRenderState;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import rotp.core.client.util.functions.ClientUtil;
import rotp.core.compat.v1_21_4.renderstate.RenderStateCrutches;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.purplehaze.entity.PurpleHazeEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public final class PurpleHazeAuraLayer extends RenderLayer<
        PurpleHazeEntity,
        PurpleHazeStandModel> {
    private static final float SHELL_SCALE = 1.035F;
    private static final float SHELL_ALPHA = 0.28F;
    private static final int AURA_COLOR = 0xFF8F3FB1;

    private final PurpleHazeStandRenderer renderer;

    public PurpleHazeAuraLayer(
            PurpleHazeStandRenderer renderer) {
        super(renderer);
        this.renderer = renderer;
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            PurpleHazeEntity entity,
            float walkAnimPos,
            float walkAnimSpeed,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        if (!(RenderStateCrutches.currentStandEntityRenderState
                instanceof PurpleHazeRenderState renderState)
                || !renderState.auraActive
                || renderState.isInvisibleToPlayer
                        && !renderState.visibleForSpectator
                || renderState.obstructionRenderMode
                        != StandEntityRenderState
                                .ObstructionRenderMode.NONE) {
            return;
        }

        float lifecycleAlpha = entity.clientStuff != null
                ? entity.clientStuff.getAlpha(
                        entity,
                        partialTick)
                : renderState.alpha;
        StandVisualContext context =
                StandVisualContext.world(
                        entity,
                        lifecycleAlpha);
        if (!PurpleHazeVisuals.canRender(context)) {
            return;
        }

        ResourceLocation texture =
                renderer.getTextureLocation(renderState);
        MultiBufferSource alphaBuffer =
                AlphaMultiBufferSource.wrap(
                        buffer,
                        context.effectiveAlpha()
                                * SHELL_ALPHA);
        VertexConsumer vertices = alphaBuffer.getBuffer(
                ModRenderTypes.standTranslucentCull(texture));
        poseStack.pushPose();
        poseStack.scale(
                SHELL_SCALE,
                SHELL_SCALE,
                SHELL_SCALE);
        getParentModel().renderClassicLayerToBuffer(
                poseStack,
                vertices,
                ClientUtil.MAX_LIGHT,
                OverlayTexture.NO_OVERLAY,
                AURA_COLOR);
        poseStack.popPose();
    }
}
