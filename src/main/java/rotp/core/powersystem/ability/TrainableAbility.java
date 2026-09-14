package rotp.core.powersystem.ability;

import javax.annotation.Nullable;

import rotp.core.powersystem.standpower.StandPower;

public interface TrainableAbility extends ProgressionSkipHandler {
	@Nullable
	String getLearningAbilityName();

	float getMaxTrainingPoints(StandPower power);

	default void onTrainingPoints(StandPower power, float points) {}

	default void onMaxTraining(StandPower power) {}

	@Override
	default void onProgressionSkipped(StandPower power) {}
}
