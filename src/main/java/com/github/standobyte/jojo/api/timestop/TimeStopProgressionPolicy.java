package com.github.standobyte.jojo.api.timestop;

import com.github.standobyte.jojo.subsystems.timestop.TimeStopLearning;

/**
 * Stand-scoped time-stop progression values. The enhanced maximum applies to
 * high-blood vampires, high-saturation zombies, and Pillar Men. Core Pillar
 * Man evolution-stage bonuses are added to this enhanced base.
 */
public record TimeStopProgressionPolicy(
		int humanMaxTicks,
		int enhancedMaxTicks,
		float learningPerTick,
		float decayPerDay,
		float cooldownPerTick) {

	public TimeStopProgressionPolicy {
		if (humanMaxTicks < TimeStopLearning.MIN_TIME_STOP_TICKS) {
			throw new IllegalArgumentException(
					"humanMaxTicks is below the time-stop minimum");
		}
		if (enhancedMaxTicks < humanMaxTicks) {
			throw new IllegalArgumentException(
					"enhancedMaxTicks must not be lower than humanMaxTicks");
		}
		if (!Float.isFinite(learningPerTick) || learningPerTick < 0.0F) {
			throw new IllegalArgumentException(
					"learningPerTick must be finite and non-negative");
		}
		if (!Float.isFinite(decayPerDay) || decayPerDay < 0.0F) {
			throw new IllegalArgumentException(
					"decayPerDay must be finite and non-negative");
		}
		if (!Float.isFinite(cooldownPerTick)) {
			throw new IllegalArgumentException(
					"cooldownPerTick must be finite");
		}
	}
}
