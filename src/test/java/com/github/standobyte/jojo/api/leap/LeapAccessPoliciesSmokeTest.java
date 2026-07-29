package com.github.standobyte.jojo.api.leap;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.resources.ResourceLocation;

public final class LeapAccessPoliciesSmokeTest {
	private LeapAccessPoliciesSmokeTest() {}

	public static void run() {
		LeapAccessPolicies.resetForTests();
		check(LeapAccessPolicies.allowsForTests(
						LeapSource.STAND,
						LeapSurface.EXECUTION),
				"empty leap policy registry changed behavior");
		ResourceLocation failed = id("failed");
		ResourceLocation passed = id("passed");
		ResourceLocation denied = id("denied");
		ResourceLocation late = id("late");
		AtomicInteger calls = new AtomicInteger();
		AtomicReference<LeapAccessQuery> seen =
				new AtomicReference<>();

		LeapAccessPolicies.register(failed, query -> {
			calls.incrementAndGet();
			throw new IllegalStateException("expected smoke failure");
		});
		LeapAccessPolicies.register(passed, query -> {
			calls.incrementAndGet();
			seen.set(query);
			return false;
		});
		LeapAccessPolicies.register(denied, query -> {
			calls.incrementAndGet();
			return true;
		});
		LeapAccessPolicies.register(late, query -> {
			calls.incrementAndGet();
			return false;
		});

		check(!LeapAccessPolicies.allowsForTests(
						LeapSource.STAND, LeapSurface.EXECUTION),
				"deny-dominant leap decision was lost");
		check(calls.get() == 3,
				"leap policies did not stop at the first deny");
		check(seen.get().source() == LeapSource.STAND
						&& seen.get().surface()
								== LeapSurface.EXECUTION,
				"leap query lost source or surface");
		check(LeapAccessPolicies.registeredOwners().equals(
						List.of(failed, passed, denied, late)),
				"leap policy registration order changed");
		expectIllegalState(() -> LeapAccessPolicies.register(
				denied, query -> false));

		LeapAccessPolicies.resetForTests();
		AtomicReference<LeapSurface> hudSurface =
				new AtomicReference<>();
		LeapAccessPolicies.register(id("hud"), query -> {
			hudSurface.set(query.surface());
			return false;
		});
		check(LeapAccessPolicies.allowsForTests(
						LeapSource.PLAYER_POWER,
						LeapSurface.HUD),
				"future HUD helper denied without a denying policy");
		check(hudSurface.get() == LeapSurface.HUD,
				"future HUD helper did not use the HUD surface");
		LeapAccessPolicies.resetForTests();
	}

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(
				"rotp_test", path);
	}

	private static void expectIllegalState(Runnable action) {
		try {
			action.run();
		}
		catch (IllegalStateException expected) {
			return;
		}
		throw new AssertionError(
				"duplicate leap policy owner was accepted");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
