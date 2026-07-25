package com.github.standobyte.jojo.powersystem.ability;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.powersystem.standpower.StandPower;

public interface TrainableAbility extends ProgressionSkipHandler {
	@Nullable
	String getLearningAbilityName();

	float getMaxTrainingPoints(StandPower power);

	default void onTrainingPoints(StandPower power, float points) {}

	default void onMaxTraining(StandPower power) {}

	@Override
	default void onProgressionSkipped(StandPower power) {}
}
