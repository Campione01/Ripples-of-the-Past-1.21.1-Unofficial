package rotp.addons.whitesnake.client;

import rotp.core.client.entityrender.stand.StandEntityModel;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.compat.v1_21_4.renderstate.RenderStateCrutches;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.whitesnake.WhitesnakeConfig;
import rotp.addons.whitesnake.entity.WhitesnakeEntity;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;

public final class WhitesnakeGlintLayer extends RenderLayer<
        WhitesnakeEntity,
        StandEntityModel<WhitesnakeEntity, WhitesnakeRenderState>> {
    public WhitesnakeGlintLayer(
            RenderLayerParent<
                    WhitesnakeEntity,
                    StandEntityModel<
                            WhitesnakeEntity,
                            WhitesnakeRenderState>> parent) {
        super(parent);
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            WhitesnakeEntity entity,
            float walkAnimationPosition,
            float walkAnimationSpeed,
            float partialTick,
            float ageInTicks,
            float headYaw,
            float headPitch) {
        if (!WhitesnakeConfig.SHOW_GLINT_ON_WHITESNAKE.get()
                || !WhitesnakeGlintRenderType.isReady()
                || !(RenderStateCrutches.currentStandEntityRenderState
                instanceof WhitesnakeRenderState renderState)
                || renderState.isInvisibleToPlayer
                        && !renderState.visibleForSpectator) {
            return;
        }

        StandVisualContext visualContext =
                StandVisualContext.world(
                        entity,
                        entity.clientStuff.getAlpha(
                                entity, partialTick));
        if (visualContext.effectiveAlpha() <= 0.0F
                || visualContext.classicObstruction()) {
            return;
        }

        VertexConsumer vertices = WhitesnakeGlintRenderType.vertices(
                buffer.getBuffer(WhitesnakeGlintRenderType.forCurrentBody()),
                visualContext.effectiveAlpha(),
                Util.getMillis(),
                Minecraft.getInstance().options.glintSpeed().get());
        StandEntityModel<
                WhitesnakeEntity,
                WhitesnakeRenderState> model =
                        getParentModel();
        model.setupAnim(renderState);

        float previousAlpha = renderState.alpha;
        renderState.alpha = 1.0F;
        try {
            model.renderToBuffer(
                    poseStack,
                    vertices,
                    packedLight,
                    OverlayTexture.NO_OVERLAY,
                    0xFFFFFFFF);
        }
        finally {
            renderState.alpha = previousAlpha;
        }
    }
}
