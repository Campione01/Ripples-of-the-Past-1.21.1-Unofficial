package com.github.standobyte.jojo.api.timestop;

import net.minecraft.resources.ResourceLocation;

public final class TimeStopAwarenessProvidersSmokeTest {
	private TimeStopAwarenessProvidersSmokeTest() {}

	public static void run() {
		TimeStopAwarenessProviders.resetForTests();
		ResourceLocation owner = ResourceLocation.fromNamespaceAndPath(
				"rotp_test", "time_stop_awareness");
		TimeStopAwarenessProviders.register(
				owner, player -> TimeStopAwareness.SEE_ONLY);
		check(TimeStopAwarenessProviders.registeredOwners()
				.equals(java.util.List.of(owner)),
				"Time-stop awareness owner registration changed");
		check(new TimeStopAwareness(false, true)
				.equals(TimeStopAwareness.FULL),
				"Movement must imply time-stop vision");
		check(TimeStopAwareness.SEE_ONLY
				.merge(new TimeStopAwareness(false, true))
				.equals(TimeStopAwareness.FULL),
				"Time-stop awareness merge changed");
		expectIllegalState(() -> TimeStopAwarenessProviders.register(
				owner, player -> TimeStopAwareness.NONE));
		TimeStopAwarenessProviders.resetForTests();
	}

	private static void expectIllegalState(Runnable action) {
		try {
			action.run();
			throw new AssertionError(
					"Duplicate time-stop provider was accepted");
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
