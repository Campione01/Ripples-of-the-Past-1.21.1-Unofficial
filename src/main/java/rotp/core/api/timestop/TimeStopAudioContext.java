package rotp.core.api.timestop;

import java.util.Objects;

import javax.annotation.Nullable;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.timestop.TimeStopState;

import net.minecraft.world.entity.LivingEntity;

public record TimeStopAudioContext(
		TimeStopAudioCue cue,
		LivingEntity user,
		@Nullable StandPower power,
		@Nullable AbilityId abilityId,
		@Nullable TimeStopState.Instance instance,
		boolean standAlreadySummoned) {

	public TimeStopAudioContext {
		Objects.requireNonNull(cue, "cue");
		Objects.requireNonNull(user, "user");
	}

	public boolean isManualResume() {
		return instance != null && instance.ticksManuallySet();
	}
}
