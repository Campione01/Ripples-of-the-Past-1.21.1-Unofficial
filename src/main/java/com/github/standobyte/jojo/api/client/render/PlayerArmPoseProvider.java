package com.github.standobyte.jojo.api.client.render;

import javax.annotation.Nullable;

import net.minecraft.client.model.HumanoidModel;

/**
 * One owner can provide a vanilla arm-pose enum and/or a final player-model
 * arm adjustment.
 */
public interface PlayerArmPoseProvider {
	@Nullable
	default HumanoidModel.ArmPose armPose(
			PlayerArmPoseQuery query) {
		return null;
	}

	default void applyPostSetup(
			PlayerArmModelQuery query) {}
}
