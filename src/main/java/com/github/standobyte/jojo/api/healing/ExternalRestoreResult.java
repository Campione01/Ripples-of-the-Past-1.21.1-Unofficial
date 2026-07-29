package com.github.standobyte.jojo.api.healing;

/**
 * Result supplied by an addon for a non-vanilla Crazy Diamond target.
 */
public record ExternalRestoreResult(
		boolean handled,
		boolean healingActive,
		boolean barrageVisuals,
		float hpForExperience) {
	private static final ExternalRestoreResult UNHANDLED =
			new ExternalRestoreResult(false, false, false, 0);

	public ExternalRestoreResult {
		if (hpForExperience < 0) {
			throw new IllegalArgumentException(
					"hpForExperience must not be negative");
		}
		if (!handled && (healingActive
				|| barrageVisuals
				|| hpForExperience != 0)) {
			throw new IllegalArgumentException(
					"an unhandled result cannot augment restoration");
		}
	}

	public static ExternalRestoreResult unhandled() {
		return UNHANDLED;
	}

	public static ExternalRestoreResult handled(
			boolean healingActive,
			boolean barrageVisuals,
			float hpForExperience) {
		return new ExternalRestoreResult(
				true,
				healingActive,
				barrageVisuals,
				hpForExperience);
	}
}
