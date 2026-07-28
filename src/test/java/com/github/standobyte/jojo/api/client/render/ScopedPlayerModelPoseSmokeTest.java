package com.github.standobyte.jojo.api.client.render;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;

public final class ScopedPlayerModelPoseSmokeTest {
	private ScopedPlayerModelPoseSmokeTest() {}

	public static void run() {
		PlayerModel<?> model = new PlayerModel<>(
				LayerDefinition.create(
						PlayerModel.createMesh(
								CubeDeformation.NONE, false),
						64, 64)
						.bakeRoot(),
				false);
		model.leftArmPose = HumanoidModel.ArmPose.BLOCK;
		model.rightArmPose = HumanoidModel.ArmPose.EMPTY;

		ScopedPlayerModelPose.ArmPoseSnapshot snapshot =
				ScopedPlayerModelPose.ArmPoseSnapshot.capture(model);
		model.leftArmPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
		model.rightArmPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
		snapshot.restore(model);

		check(model.leftArmPose == HumanoidModel.ArmPose.BLOCK,
				"left arm pose was not restored");
		check(model.rightArmPose == HumanoidModel.ArmPose.EMPTY,
				"right arm pose was not restored");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
