package rotp.addons.rhcp.client;

import rotp.core.client.entityrender.stand.StandEntityRenderState;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.ModRenderTypes;
import rotp.core.client.util.functions.ClientUtil;
import rotp.core.compat.v1_21_4.renderstate.RenderStateCrutches;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.rhcp.RhcpAddon;
import rotp.addons.rhcp.entity.RhcpStandEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class RhcpGlowLayer extends RenderLayer<
        RhcpStandEntity, RhcpModel> {
    private static final ResourceLocation GLOW =
            RhcpAddon.id("textures/entity/stand/glow/rhcp.png");

    public RhcpGlowLayer(RhcpRenderer renderer) {
        super(renderer);
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            RhcpStandEntity entity,
            float walkAnimPos,
            float walkAnimSpeed,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        if (!(RenderStateCrutches.currentStandEntityRenderState
                instanceof RhcpRenderState state)
                || state.potency <= 0
                || state.inConductor
                || state.isInvisibleToPlayer
                        && !state.visibleForSpectator
                || state.obstructionRenderMode
                        != StandEntityRenderState
                                .ObstructionRenderMode.NONE) {
            return;
        }
        StandVisualContext visual = state.visualContext;
        if (visual == null || visual.effectiveAlpha() <= 0.0F) {
            return;
        }
        int alpha = Mth.clamp(Math.round(visual.effectiveAlpha() * 255.0F), 0, 255);
        VertexConsumer vertices = buffer.getBuffer(
                ModRenderTypes.standSurfaceGlow(GLOW, true));
        getParentModel().renderClassicLayerToBuffer(
                poseStack,
                vertices,
                ClientUtil.MAX_LIGHT,
                OverlayTexture.NO_OVERLAY,
                (alpha << 24) | 0x00FFFFFF);
    }
}
