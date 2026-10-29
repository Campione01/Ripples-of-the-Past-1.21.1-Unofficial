package rotp.addons.ger.client;

import rotp.core.client.entityrender.stand.StandEntityModel;
import rotp.core.client.entityrender.stand.StandEntityRenderState;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.compat.v1_21_4.renderstate.RenderStateCrutches;
import rotp.core.init.ModItems;
import rotp.core.powersystem.standpower.entity.StandEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import rotp.addons.ger.GoldExperienceRequiemAddon;
import rotp.addons.ger.GoldExperienceRequiemGameEvents;
import rotp.addons.ger.state.GerPlayerState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * The 1.16 {@code GoldExperienceBeetleArrowLayer}: while the user's evolution runs, Gold Experience holds
 * the beetle arrow in its right hand, easing from the "about to stab" pose to the "arrow in" pose over
 * ticks 20-22 of the evolution (the counters are synced through {@link GerPlayerState#STREAM_CODEC}).
 */
public final class GerBeetleArrowLayer extends RenderLayer<
        StandEntity, StandEntityModel<StandEntity, StandEntityRenderState>> {
    private static final float ARROW_1_X = 0.0F;
    private static final float ARROW_1_Y = -0.6F;
    private static final float ARROW_1_Z = -0.35F;
    private static final float ARROW_1_ROT_X = 25.0F;
    private static final float ARROW_1_ROT_Y = 0.0F;
    private static final float ARROW_1_ROT_Z = -50.0F;
    private static final float ARROW_2_X = 0.4F;
    private static final float ARROW_2_Y = 0.5F;
    private static final float ARROW_2_Z = 0.0F;
    private static final float ARROW_2_ROT_X = 50.0F;
    private static final float ARROW_2_ROT_Y = 20.0F;
    private static final float ARROW_2_ROT_Z = 90.0F;
    private static final float ARROW_SCALE = 0.55F;

    private ItemStack beetleArrow;

    public GerBeetleArrowLayer(
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
        if (!(entity.getUser() instanceof Player user)) {
            return;
        }
        GerPlayerState state = GoldExperienceRequiemAddon.playerState(user);
        if (state == null || !state.isEvolving()) {
            return;
        }
        StandEntityRenderState renderState = RenderStateCrutches.currentStandEntityRenderState;
        if (renderState != null) {
            if (renderState.isInvisibleToPlayer && !renderState.visibleForSpectator
                    || renderState.obstructionRenderMode
                            != StandEntityRenderState.ObstructionRenderMode.NONE) {
                return;
            }
            StandVisualContext visual = renderState.visualContext;
            if (visual != null && visual.effectiveAlpha() <= 0.0F) {
                return;
            }
        }
        if (beetleArrow == null) {
            beetleArrow = new ItemStack(ModItems.STAND_ARROW_BEETLE.get());
        }

        float pose2 = pose2Progress(state, partialTick);
        poseStack.pushPose();
        // the path to right_item starts at the model root, so this includes the 1.16 root transform
        getParentModel().translateToHand(HumanoidArm.RIGHT, poseStack);
        poseStack.translate(
                Mth.lerp(pose2, ARROW_1_X, ARROW_2_X),
                Mth.lerp(pose2, ARROW_1_Y, ARROW_2_Y),
                Mth.lerp(pose2, ARROW_1_Z, ARROW_2_Z));
        poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(pose2, ARROW_1_ROT_X, ARROW_2_ROT_X)));
        poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(pose2, ARROW_1_ROT_Y, ARROW_2_ROT_Y)));
        poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(pose2, ARROW_1_ROT_Z, ARROW_2_ROT_Z)));
        poseStack.scale(ARROW_SCALE, ARROW_SCALE, ARROW_SCALE);
        Minecraft.getInstance().getItemRenderer().renderStatic(
                beetleArrow,
                ItemDisplayContext.NONE,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                buffer,
                entity.level(),
                entity.getId());
        poseStack.popPose();
    }

    /** 0 until tick 20, cubic ease-out to 1 by tick 22 (the client counter advances on each sync). */
    private static float pose2Progress(GerPlayerState state, float partialTick) {
        float elapsed = state.evolutionElapsed() + partialTick;
        float start = GoldExperienceRequiemGameEvents.ARROW_POSE_START_TICK;
        float end = GoldExperienceRequiemGameEvents.ARROW_POSE_END_TICK;
        if (elapsed <= start) {
            return 0.0F;
        }
        if (elapsed >= end) {
            return 1.0F;
        }
        float u = 1.0F - (elapsed - start) / (end - start);
        return 1.0F - u * u * u;
    }
}
