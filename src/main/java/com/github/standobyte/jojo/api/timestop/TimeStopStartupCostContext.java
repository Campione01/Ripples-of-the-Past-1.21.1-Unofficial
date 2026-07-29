package com.github.standobyte.jojo.api.timestop;

import java.util.Objects;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.subsystems.timestop.TimeStopState;

/**
 * Server-side startup-cost query. {@link #instance()} is {@code null} during
 * the early action-admission query and is the post-PreStart instance during
 * the authoritative consume query.
 */
public record TimeStopStartupCostContext(
		StandPower power,
		AbilityId abilityId,
		@Nullable TimeStopState.Instance instance,
		float defaultCost) {

	public TimeStopStartupCostContext {
		Objects.requireNonNull(power, "power");
		Objects.requireNonNull(abilityId, "abilityId");
		if (!Float.isFinite(defaultCost) || defaultCost < 0.0F) {
			throw new IllegalArgumentException(
					"defaultCost must be finite and non-negative");
		}
	}

	public boolean isAdmissionQuery() {
		return instance == null;
	}
}
