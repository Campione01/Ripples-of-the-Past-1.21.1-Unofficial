package rotp.addons.vampire_climb.client;

import rotp.core.api.client.render.FirstPersonPostArmLayers;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import rotp.addons.vampire_climb.ability.VampireWallClimbAbility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.HumanoidArm;

public final class VampireClimbArmLayer {
    private static final float SECOND_ARM_X_OFFSET = 0.85F;
    private static final float SECOND_ARM_Y_OFFSET = -0.18F;
    private static final float SECOND_ARM_Z_OFFSET = -0.22F;
    private static final float SECOND_ARM_ROLL_DEGREES = 18.0F;

    private VampireClimbArmLayer() {}

    public static void render(
            FirstPersonPostArmLayers.Context arm) {
        if (!(arm.entity() instanceof AbstractClientPlayer player)
                || player != Minecraft.getInstance().player
                || player.isSpectator()
                || player.isInvisible()
                || arm.handSide() != player.getMainArm()
                || !player.getMainHandItem().isEmpty()
                || !player.getOffhandItem().isEmpty()
                || !isWallClimbing(player)) {
            return;
        }

        float side = arm.handSide() == HumanoidArm.RIGHT
                ? 1.0F
                : -1.0F;
        PoseStack poseStack = arm.poseStack();
        poseStack.pushPose();
        poseStack.translate(
                -side * SECOND_ARM_X_OFFSET,
                SECOND_ARM_Y_OFFSET,
                SECOND_ARM_Z_OFFSET);
        poseStack.mulPose(Axis.ZP.rotationDegrees(
                side * SECOND_ARM_ROLL_DEGREES));
        VertexConsumer vertices = arm.buffer().getBuffer(
                RenderType.entityTranslucent(
                        player.getSkin().texture()));
        arm.renderArm(vertices);
        poseStack.popPose();
    }

    static boolean isWallClimbing(
            AbstractClientPlayer player) {
        EntityActionInstance action =
                LivingComponentAction.getCurEntityAction(player);
        return action != null
                && action.ability
                        instanceof VampireWallClimbAbility;
    }
}
