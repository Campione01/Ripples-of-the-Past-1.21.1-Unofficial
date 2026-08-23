package com.github.standobyte.jojo.client.entityrender.stand.aura;

import com.github.standobyte.jojo.api.client.render.EntityMaskPostEffect;
import com.github.standobyte.jojo.client.entityrender.stand.StandEntityModel;
import com.github.standobyte.jojo.client.entityrender.stand.StandEntityRenderState;
import com.github.standobyte.jojo.client.entityrender.stand.StandEntityRenderer;
import com.github.standobyte.jojo.client.rendertype.AlphaMultiBufferSource;
import com.github.standobyte.jojo.client.rendertype.ModRenderTypes;
import com.github.standobyte.jojo.client.util.functions.ClientUtil;
import com.github.standobyte.jojo.config.client.ClientModSettings;
import com.github.standobyte.jojo.config.client.StandAuraSettings;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.v1_21_4_stuff.renderstate.RenderStateCrutches;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Stand render-layer owner for the integrated StandAuraFx effect. */
public final class StandAuraLayer<
        T extends StandEntity,
        S extends StandEntityRenderState,
        M extends StandEntityModel<T, S>>
        extends RenderLayer<T, M> {
    private final StandEntityRenderer<T, S, M> renderer;

    public StandAuraLayer(StandEntityRenderer<T, S, M> renderer) {
        super(renderer);
        this.renderer = renderer;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            T entity,
            float walkAnimPos,
            float walkAnimSpeed,
            float partialTick,
            float ticks,
            float headYRotation,
            float headXRotation) {
        StandAuraSettings settings =
                ClientModSettings.getSettingsReadOnly().standAura;
        StandEntityRenderState state =
                RenderStateCrutches.currentStandEntityRenderState;
        if (!settings.enabled
                || EntityMaskPostEffect.isCapturePass()
                || state == null
                || state.alpha <= 0.0F
                || state.isInvisibleToPlayer
                        && !state.visibleForSpectator) {
            return;
        }

        StandAuraRenderRequests.Decision decision =
                StandAuraRenderRequests.decisionFor(entity);
        if (!decision.render()) {
            return;
        }
        float alpha = state.visualContext != null
                ? state.visualContext.effectiveAlpha()
                : state.alpha;
        if (alpha <= 0.0F) {
            return;
        }

        int color = decision.color() != null
                ? decision.color() & 0xFFFFFF
                : state.skin != null
                        ? state.skin.getColor() & 0xFFFFFF
                        : StandAuraSettings.FALLBACK_COLOR;
        if (StandAuraMaskCompositor.queue(
                entity, color, alpha)) {
            return;
        }

        S typedState = (S) state;
        M model = getParentModel();
        ResourceLocation texture =
                renderer.getTextureLocation(typedState);
        int passes = StandAuraShellStyle.passCount();
        float width = StandAuraShellStyle.width(entity, partialTick);
        float fallbackAlpha = alpha
                * Math.min(settings.globalAlpha, 1.0F)
                * 0.28F / passes;
        MultiBufferSource alphaBuffer =
                AlphaMultiBufferSource.wrap(buffer, fallbackAlpha);
        VertexConsumer consumer = alphaBuffer.getBuffer(
                ModRenderTypes.standTranslucent(texture));
        for (int pass = 0; pass < passes; pass++) {
            float shellScale = StandAuraShellStyle.scale(
                    pass, passes, width);
            poseStack.pushPose();
            try {
                poseStack.scale(
                        shellScale, shellScale, shellScale);
                model.renderClassicLayerToBuffer(
                        poseStack,
                        consumer,
                        ClientUtil.MAX_LIGHT,
                        OverlayTexture.NO_OVERLAY,
                        0xFF000000 | color);
            }
            finally {
                poseStack.popPose();
            }
        }
    }
}
