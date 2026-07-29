package com.github.standobyte.jojo.api.stand;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.ResourceLocation;

public final class StandLeapUnlockProvidersSmokeTest {
	private StandLeapUnlockProvidersSmokeTest() {}

	public static void run() {
		StandLeapUnlockProviders.resetForTests();
		ResourceLocation first = id("first");
		ResourceLocation second = id("second");
		List<ResourceLocation> calls = new ArrayList<>();
		StandLeapUnlockProviders.register(first, query -> {
			calls.add(first);
			throw new IllegalStateException("isolated test failure");
		});
		StandLeapUnlockProviders.register(second, query -> {
			calls.add(second);
			return true;
		});
		check(StandLeapUnlockProviders.evaluate(
						new StandLeapUnlockQuery(null, null, null)),
				"Stand leap providers did not OR successful results");
		check(calls.equals(List.of(first, second)),
				"Stand leap provider order or exception isolation changed");
		check(StandLeapUnlockProviders.registeredOwners()
						.equals(List.of(first, second)),
				"Stand leap provider owner order changed");
		expectIllegalState(() -> StandLeapUnlockProviders.register(
				first, query -> false));
		StandLeapUnlockProviders.resetForTests();
	}

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath("rotp_test", path);
	}

	private static void expectIllegalState(Runnable action) {
		try {
			action.run();
			throw new AssertionError("Duplicate provider was accepted");
		}
		catch (IllegalStateException expected) {
			// Expected.
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
