package com.github.standobyte.jojo.mechanics.standarrow;

import com.github.standobyte.jojo.mechanics.standarrow.StandVirusMobGiverLifecyclePolicy.Resolution;

public final class StandVirusMobGiverLifecyclePolicySmokeTest {
	private StandVirusMobGiverLifecyclePolicySmokeTest() {}

	public static void run() {
		survivalChanceBoundariesAreStrict();

		check(decide(false, false, false) == Resolution.CORE_GRANT,
				"unowned virus must retain the core grant path");
		check(decide(false, true, false) == Resolution.MOB_GIVER,
				"newly matched provider must own resolution");
		check(decide(true, true, false) == Resolution.MOB_GIVER,
				"persisted available owner must resume provider resolution");

		Resolution missingAfterReload = decide(true, false, false);
		check(missingAfterReload == Resolution.FAIL_CLOSED,
				"persisted missing owner must fail closed after reload");
		check(!missingAfterReload.allowsCoreGrant(),
				"missing owner must never fall back to a core grant");
		check(missingAfterReload.clearsWithoutGrant(),
				"missing owner must terminate and clear its virus");

		Resolution stopAfterCleanup = decide(true, false, true);
		check(stopAfterCleanup == Resolution.ALREADY_HANDLED,
				"stop after missing-owner cleanup must be idempotent");
		check(!stopAfterCleanup.allowsCoreGrant(),
				"second stop must not grant a core Stand");
		check(stopAfterCleanup.clearsWithoutGrant(),
				"handled missing-owner lifecycle must remain terminal");
	}

	private static void survivalChanceBoundariesAreStrict() {
		check(!StandVirusMobGiverLifecyclePolicy
						.passesSurvivalRoll(0.0F, 0.0F),
				"a zero survival chance accepted the minimum random roll");
		check(!StandVirusMobGiverLifecyclePolicy
						.passesSurvivalRoll(0.0F, -1.0F),
				"a negative survival chance was not clamped to zero");
		check(StandVirusMobGiverLifecyclePolicy
						.passesSurvivalRoll(0.0F, 1.0F),
				"a full survival chance rejected the minimum random roll");
		check(StandVirusMobGiverLifecyclePolicy.passesSurvivalRoll(
						Math.nextDown(1.0F), 1.0F),
				"a full survival chance rejected a valid random roll");
		check(!StandVirusMobGiverLifecyclePolicy
						.passesSurvivalRoll(0.5F, 0.5F),
				"the survival threshold remained inclusive");
	}

	private static Resolution decide(
			boolean hasPersistedOwner,
			boolean providerAvailable,
			boolean resolutionHandled) {
		return StandVirusMobGiverLifecyclePolicy.decide(
				hasPersistedOwner,
				providerAvailable,
				resolutionHandled);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
