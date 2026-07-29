package com.github.standobyte.jojo.api.stand;

import java.util.Set;

import net.minecraft.resources.ResourceLocation;

public final class StandArrowPoolOverridesSmokeTest {
	private StandArrowPoolOverridesSmokeTest() {}

	public static void run() {
		ResourceLocation ownerA = id("rotp_test", "owner_a");
		ResourceLocation ownerB = id("rotp_test", "owner_b");
		ResourceLocation cMoon = id("rotp_cm", "cmoon");
		ResourceLocation madeInHeaven =
				id("rotp_mih", "made_in_heaven");

		StandArrowPoolOverrides.resetForTests();
		check(!StandArrowPoolOverrides.isExcluded(cMoon),
				"unregistered Stand exclusion leaked");

		StandArrowPoolOverrides.exclude(ownerA, cMoon);
		StandArrowPoolOverrides.exclude(ownerA, cMoon);
		StandArrowPoolOverrides.exclude(ownerA, madeInHeaven);
		StandArrowPoolOverrides.exclude(ownerB, cMoon);

		check(StandArrowPoolOverrides.isExcluded(cMoon),
				"registered Stand was not excluded");
		check(StandArrowPoolOverrides.isExcluded(madeInHeaven),
				"second registered Stand was not excluded");
		check(StandArrowPoolOverrides.ownersExcluding(cMoon)
						.equals(Set.of(ownerA, ownerB)),
				"owner-scoped exclusion snapshot drifted");
		check(StandArrowPoolOverrides
						.ownersExcluding(madeInHeaven)
						.equals(Set.of(ownerA)),
				"single owner exclusion snapshot drifted");

		Set<ResourceLocation> snapshot =
				StandArrowPoolOverrides.ownersExcluding(cMoon);
		expectUnsupported(() -> snapshot.add(
				id("rotp_test", "mutating_owner")));
		check(StandArrowPoolOverrides.ownersExcluding(cMoon)
						.equals(Set.of(ownerA, ownerB)),
				"caller mutated registered exclusion owners");

		StandArrowPoolOverrides.resetForTests();
		check(!StandArrowPoolOverrides.isExcluded(cMoon)
						&& !StandArrowPoolOverrides
								.isExcluded(madeInHeaven),
				"test reset retained Stand exclusions");
	}

	private static ResourceLocation id(
			String namespace, String path) {
		return ResourceLocation.fromNamespaceAndPath(
				namespace, path);
	}

	private static void expectUnsupported(Runnable action) {
		try {
			action.run();
			throw new AssertionError(
					"Expected immutable exclusion snapshot");
		}
		catch (UnsupportedOperationException expected) {}
	}

	private static void check(
			boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
