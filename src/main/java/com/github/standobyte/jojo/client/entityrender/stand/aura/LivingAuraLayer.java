package com.github.standobyte.jojo.client.entityrender.stand.aura;

import com.github.standobyte.jojo.api.client.render.EntityMaskPostEffect;
import com.github.standobyte.jojo.api.client.render.LivingEntityRenderLayerContext;
import com.github.standobyte.jojo.api.client.render.LivingEntityRenderLayerProvider;
import com.github.standobyte.jojo.client.util.functions.ClientUtil;
import com.github.standobyte.jojo.config.client.ClientModSettings;
import com.github.standobyte.jojo.config.client.StandAuraSettings;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

final class LivingAuraLayer
        implements LivingEntityRenderLayerProvider {
    @Override
    public void render(LivingEntityRenderLayerContext context) {
        StandAuraSettings settings =
                ClientModSettings.getSettingsReadOnly().standAura;
        LivingEntity entity = context.entity();
        if (!settings.enabled
                || EntityMaskPostEffect.isCapturePass()
                || entity instanceof StandEntity
                || isInvisibleToLocalPlayer(entity)) {
            return;
        }
        StandAuraRenderRequests.Decision decision =
                StandAuraRenderRequests.decisionFor(entity);
        if (!decision.render()) {
            return;
        }

        int color = StandAuraColors.forLiving(
                entity, decision.color());
        if (StandAuraMaskCompositor.queue(entity, color, 1.0F)) {
            return;
        }

        int passes = StandAuraShellStyle.passCount();
        float width = StandAuraShellStyle.width(
                entity, context.partialTick());
        PoseStack poseStack = context.poseStack();
        VertexConsumer consumer = context.getBuffer(
                RenderType.entityTranslucent(texture(entity)));
        int fallbackAlpha = Math.round(
                Math.min(settings.globalAlpha, 1.0F)
                        * 72.0F / passes);
        for (int pass = 0; pass < passes; pass++) {
            float shellScale = StandAuraShellStyle.scale(
                    pass, passes, width);
            poseStack.pushPose();
            try {
                poseStack.scale(
                        shellScale, shellScale, shellScale);
                context.renderModel(
                        consumer,
                        ClientUtil.MAX_LIGHT,
                        OverlayTexture.NO_OVERLAY,
                        fallbackAlpha << 24 | color);
            }
            finally {
                poseStack.popPose();
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ResourceLocation texture(LivingEntity entity) {
        EntityRenderer renderer = Minecraft.getInstance()
                .getEntityRenderDispatcher()
                .getRenderer(entity);
        return renderer.getTextureLocation(entity);
    }

    private static boolean isInvisibleToLocalPlayer(
            LivingEntity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null
                ? entity.isInvisibleTo(minecraft.player)
                : entity.isInvisible();
    }
}
