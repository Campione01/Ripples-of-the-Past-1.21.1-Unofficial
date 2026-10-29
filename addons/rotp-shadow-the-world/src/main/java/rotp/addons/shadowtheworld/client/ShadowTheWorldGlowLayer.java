package rotp.addons.shadowtheworld.client;

import rotp.core.client.entityrender.stand.StandEntityModel;
import rotp.core.client.entityrender.stand.StandEntityRenderState;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.ModRenderTypes;
import rotp.core.client.standskin.StandSkin;
import rotp.core.client.standskin.StandSkinsLoader;
import rotp.core.client.util.functions.ClientUtil;
import rotp.core.compat.v1_21_4.renderstate.RenderStateCrutches;
import rotp.core.powersystem.standpower.entity.StandEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.shadowtheworld.ShadowTheWorldAddon;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * The 1.16 {@code SHADOWWORLDGlowLayer}: once the user has consumed the Nether Star, the Stand is drawn
 * twice more with the skin's shade texture at full brightness, jittered by ±0.015 on a 20-tick sine, which
 * reads as a dark aura. The port ships the shade textures per skin as {@code textures/entity/glow/shadowtheworld.png}.
 */
public final class ShadowTheWorldGlowLayer extends RenderLayer<
        StandEntity, StandEntityModel<StandEntity, StandEntityRenderState>> {
    private static final ResourceLocation GLOW =
            ShadowTheWorldAddon.id("textures/entity/glow/shadowtheworld.png");
    private static final float JITTER = 0.015F;
    private static final float JITTER_PERIOD = (float) Math.PI / 10.0F;

    public ShadowTheWorldGlowLayer(
            RenderLayerParent<StandEntity, StandEntityModel<StandEntity, StandEntityRenderState>> parent) {
        super(parent);
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            StandEntity entity,
            float walkAnimPos,
            float walkAnimSpeed,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        if (!(entity.getUser() instanceof Player user)
                || !ShadowTheWorldAddon.state(user).usedNetherStar()) {
            return;
        }
        StandEntityRenderState state = RenderStateCrutches.currentStandEntityRenderState;
        if (state == null
                || state.isInvisibleToPlayer && !state.visibleForSpectator
                || state.obstructionRenderMode != StandEntityRenderState.ObstructionRenderMode.NONE) {
            return;
        }
        StandVisualContext visual = state.visualContext;
        if (visual == null || visual.effectiveAlpha() <= 0.0F) {
            return;
        }
        int alpha = Mth.clamp(Math.round(visual.effectiveAlpha() * 255.0F), 0, 255);
        int color = (alpha << 24) | 0x00FFFFFF;
        StandSkin skin = StandSkinsLoader.getInstance().getSkin(entity);
        ResourceLocation texture = skin != null ? skin.getTexture(GLOW) : GLOW;
        VertexConsumer vertices = buffer.getBuffer(ModRenderTypes.standSurfaceGlow(texture, true));

        float tick = (entity.tickCount + partialTick) * JITTER_PERIOD;
        float x = Mth.sin(tick) * JITTER;
        float y = Mth.cos(tick) * JITTER;
        float z = y;
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        getParentModel().renderClassicLayerToBuffer(
                poseStack, vertices, ClientUtil.MAX_LIGHT, OverlayTexture.NO_OVERLAY, color);
        poseStack.translate(-2.0F * x, -2.0F * y, -2.0F * z);
        getParentModel().renderClassicLayerToBuffer(
                poseStack, vertices, ClientUtil.MAX_LIGHT, OverlayTexture.NO_OVERLAY, color);
        poseStack.popPose();
    }
}
