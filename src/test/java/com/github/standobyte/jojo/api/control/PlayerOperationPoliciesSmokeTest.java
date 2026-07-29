package com.github.standobyte.jojo.api.control;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.resources.ResourceLocation;

public final class PlayerOperationPoliciesSmokeTest {
	private PlayerOperationPoliciesSmokeTest() {}

	public static void run() {
		PlayerOperationPolicies.resetForTests();
		check(!PlayerOperationPolicies.evaluateForTests(
						PlayerOperation.MENU_OPEN_STANDARD)
						.denied(),
				"empty operation policy registry changed behavior");
		ResourceLocation failed = id("failed");
		ResourceLocation passed = id("passed");
		ResourceLocation denied = id("denied");
		ResourceLocation late = id("late");
		AtomicInteger calls = new AtomicInteger();

		PlayerOperationPolicies.register(failed, query -> {
			calls.incrementAndGet();
			throw new IllegalStateException("expected smoke failure");
		});
		PlayerOperationPolicies.register(passed, query -> {
			calls.incrementAndGet();
			return PlayerOperationDecision.pass();
		});
		PlayerOperationPolicies.register(denied, query -> {
			calls.incrementAndGet();
			return PlayerOperationDecision.deny();
		});
		PlayerOperationPolicies.register(late, query -> {
			calls.incrementAndGet();
			return PlayerOperationDecision.pass();
		});

		PlayerOperationDecision decision =
				PlayerOperationPolicies.evaluateForTests(
						PlayerOperation.MENU_OPEN_STANDARD);
		check(decision.denied(),
				"deny-dominant operation decision was lost");
		check(calls.get() == 3,
				"operation policies did not stop at the first deny");
		check(PlayerOperationPolicies.registeredOwners().equals(
						List.of(failed, passed, denied, late)),
				"operation policy registration order changed");
		expectIllegalState(() -> PlayerOperationPolicies.register(
				denied, query -> PlayerOperationDecision.pass()));
		PlayerOperationPolicies.resetForTests();
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
				"duplicate operation policy owner was accepted");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
