package rotp.core.api.timestop;

import rotp.core.subsystems.timestop.TimeStopLearning;

/**
 * Stand-scoped time-stop progression values, as set by the 1.16 TimeStop.Builder.
 * The enhanced maximum applies to high-blood vampires and Pillar Men; core Pillar
 * Man evolution-stage bonuses are added to it. The zombie maximum applies to
 * high-saturation zombies, who are checked first (1.16 getMaxTimeStopTicks) and
 * never get less than humans (1.16 forZombie = max(forHuman, forZombie)).
 */
public record TimeStopProgressionPolicy(
		int humanMaxTicks,
		int enhancedMaxTicks,
		int zombieMaxTicks,
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
		if (zombieMaxTicks < humanMaxTicks) {
			throw new IllegalArgumentException(
					"zombieMaxTicks must not be lower than humanMaxTicks");
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

	/**
	 * Zombies share the enhanced maximum, like the 1.16 two-value
	 * timeStopMaxTicks(forHuman, forVampire). Kept for existing callers.
	 */
	public TimeStopProgressionPolicy(
			int humanMaxTicks,
			int enhancedMaxTicks,
			float learningPerTick,
			float decayPerDay,
			float cooldownPerTick) {
		this(humanMaxTicks, enhancedMaxTicks, enhancedMaxTicks,
				learningPerTick, decayPerDay, cooldownPerTick);
	}
}
