package com.github.standobyte.jojo.mechanics.standarrow;

final class StandVirusMobGiverLifecyclePolicy {
	private StandVirusMobGiverLifecyclePolicy() {}

	static Resolution decide(
			boolean hasPersistedOwner,
			boolean providerAvailable,
			boolean resolutionHandled) {
		if (resolutionHandled) {
			return Resolution.ALREADY_HANDLED;
		}
		if (hasPersistedOwner && !providerAvailable) {
			return Resolution.FAIL_CLOSED;
		}
		return providerAvailable
				? Resolution.MOB_GIVER
				: Resolution.CORE_GRANT;
	}

	static boolean passesSurvivalRoll(
			float roll, float configuredChance) {
		float chance = Math.max(
				0.0F, Math.min(1.0F, configuredChance));
		return roll < chance;
	}

	enum Resolution {
		CORE_GRANT(true, false),
		MOB_GIVER(false, false),
		FAIL_CLOSED(false, true),
		ALREADY_HANDLED(false, true);

		private final boolean allowsCoreGrant;
		private final boolean clearsWithoutGrant;

		Resolution(boolean allowsCoreGrant, boolean clearsWithoutGrant) {
			this.allowsCoreGrant = allowsCoreGrant;
			this.clearsWithoutGrant = clearsWithoutGrant;
		}

		boolean allowsCoreGrant() {
			return allowsCoreGrant;
		}

		boolean clearsWithoutGrant() {
			return clearsWithoutGrant;
		}
	}
}
