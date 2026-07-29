package com.github.standobyte.jojo.api.healing;

/**
 * Additive result from a post-living Crazy Diamond restore observer.
 */
public record RestoreAugmentation(
		boolean healingActive,
		boolean barrageVisuals,
		float hpForExperience) {
	private static final RestoreAugmentation NONE =
			new RestoreAugmentation(false, false, 0);

	public RestoreAugmentation {
		if (hpForExperience < 0) {
			throw new IllegalArgumentException(
					"hpForExperience must not be negative");
		}
	}

	public static RestoreAugmentation none() {
		return NONE;
	}
}
