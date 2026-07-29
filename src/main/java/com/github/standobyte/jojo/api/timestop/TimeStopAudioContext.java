package com.github.standobyte.jojo.api.timestop;

import java.util.Objects;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.subsystems.timestop.TimeStopState;

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
