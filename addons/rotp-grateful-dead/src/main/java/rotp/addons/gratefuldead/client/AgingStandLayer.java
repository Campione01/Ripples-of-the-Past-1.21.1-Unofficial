package rotp.addons.gratefuldead.client;

import rotp.core.client.entityrender.stand.StandEntityModel;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import rotp.core.compat.v1_21_4.renderstate.RenderStateCrutches;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.gratefuldead.GratefulDeadAddon;
import rotp.addons.gratefuldead.entity.GratefulDeadStandEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public final class AgingStandLayer extends RenderLayer<
        GratefulDeadStandEntity,
        StandEntityModel<
                GratefulDeadStandEntity,
                GratefulDeadRenderState>> {
    private static final ResourceLocation TEXTURE =
            GratefulDeadAddon.id(
                    "textures/entity/"
                            + "the_grateful_dead_aging.png");

    public AgingStandLayer(
            RenderLayerParent<
                            GratefulDeadStandEntity,
                            StandEntityModel<
                                    GratefulDeadStandEntity,
                                    GratefulDeadRenderState>>
                    parent) {
        super(parent);
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            GratefulDeadStandEntity entity,
            float walkAnimPos,
            float walkAnimSpeed,
            float partialTick,
            float ticks,
            float headYRotation,
            float headXRotation) {
        if (!(RenderStateCrutches
                        .currentStandEntityRenderState
                instanceof GratefulDeadRenderState state)
                || state.controllerAging <= 0.0F) {
            return;
        }
        StandVisualContext context = state.visualContext;
        if (context == null
                || context.effectiveAlpha() <= 0.0F
                || state.isInvisibleToPlayer
                        && !state.visibleForSpectator) {
            return;
        }
        StandEntityModel<
                        GratefulDeadStandEntity,
                        GratefulDeadRenderState>
                model = getParentModel();
        model.setupAnim(state);
        ResourceLocation texture = state.skin != null
                ? state.skin.getTexture(TEXTURE)
                : TEXTURE;
        MultiBufferSource agingBuffer =
                AlphaMultiBufferSource.wrap(
                        buffer, state.controllerAging);
        VertexConsumer vertices = agingBuffer.getBuffer(
                ModRenderTypes.standTranslucent(
                        texture));
        model.renderToBuffer(
                poseStack,
                vertices,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                0xFFFFFFFF);
    }
}
