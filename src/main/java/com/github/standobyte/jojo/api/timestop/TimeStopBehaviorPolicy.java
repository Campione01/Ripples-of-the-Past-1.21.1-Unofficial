package com.github.standobyte.jojo.api.timestop;

import javax.annotation.Nullable;

/**
 * Stand-scoped time-stop behavior supplied by an addon. Implementations must
 * be side-effect free because startup-cost queries may run both during action
 * admission and immediately before the server commits a time stop.
 */
public interface TimeStopBehaviorPolicy {
	default TimeStopStartupCostDecision startupCost(
			TimeStopStartupCostContext context) {
		return TimeStopStartupCostDecision.pass();
	}

	default TimeStopAudioDecision audio(TimeStopAudioContext context) {
		return TimeStopAudioDecision.pass();
	}

	@Nullable
	default TimeStopProgressionPolicy progression() {
		return null;
	}
}
