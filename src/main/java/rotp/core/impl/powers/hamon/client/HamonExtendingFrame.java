package rotp.core.impl.powers.hamon.client;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import rotp.core.util.functions.MathUtil;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.world.phys.Vec3;

/**
 * Frame of an extending strip, relative to the entity position, as 1.16.5 ExtendingEntityRenderer sets it up: the model
 * flip (1, -1, -1), then the yaw and pitch of extentVec (entity position minus origin point). The strip starts at the
 * entity and its +z axis runs back to the origin point.
 */
public final class HamonExtendingFrame {
    private HamonExtendingFrame() {}

    private static Quaternionf orientation(Vec3 extentVec) {
        return Axis.YP.rotationDegrees(MathUtil.yRotDegFromVec(extentVec))
                .mul(Axis.XP.rotationDegrees(MathUtil.xRotDegFromVec(extentVec)));
    }

    public static Matrix4f frame(Vec3 extentVec) {
        return new Matrix4f().scale(1.0F, -1.0F, -1.0F).rotate(orientation(extentVec));
    }

    public static void apply(PoseStack poseStack, Vec3 extentVec) {
        poseStack.scale(1.0F, -1.0F, -1.0F);
        poseStack.mulPose(orientation(extentVec));
    }
}
