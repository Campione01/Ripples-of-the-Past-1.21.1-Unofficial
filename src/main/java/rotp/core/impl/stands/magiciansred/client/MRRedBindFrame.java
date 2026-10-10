package rotp.core.impl.stands.magiciansred.client;

import rotp.core.util.functions.MathUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.world.phys.Vec3;

/**
 * Frame of a Red Bind strip, relative to the entity position, as 1.16.5 ExtendingEntityRenderer sets it up: the model flip
 * (1, -1, -1), then the yaw and pitch of extentVec (entity position minus origin point). The strip starts at the entity
 * and its +z axis runs back to the origin point.
 */
public final class MRRedBindFrame {
	private MRRedBindFrame() {}

	public static void apply(PoseStack poseStack, Vec3 extentVec) {
		poseStack.scale(1.0F, -1.0F, -1.0F);
		poseStack.mulPose(Axis.YP.rotationDegrees(MathUtil.yRotDegFromVec(extentVec)));
		poseStack.mulPose(Axis.XP.rotationDegrees(MathUtil.xRotDegFromVec(extentVec)));
	}
}
