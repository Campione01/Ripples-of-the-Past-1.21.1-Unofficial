package com.github.standobyte.jojo.api.stand;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.resources.ResourceLocation;

public final class StandDamageAuthorizersSmokeTest {
	private StandDamageAuthorizersSmokeTest() {}

	public static void run() {
		StandDamageAuthorizers.resetForTests();
		ResourceLocation first = id("first");
		ResourceLocation second = id("second");
		AtomicInteger calls = new AtomicInteger();

		StandDamageAuthorizers.register(first, query -> {
			calls.incrementAndGet();
			return false;
		});
		StandDamageAuthorizers.register(second, query -> {
			calls.incrementAndGet();
			return true;
		});

		check(StandDamageAuthorizers.registeredOwners()
						.equals(List.of(first, second)),
				"Stand damage authorizer order changed");
		check(StandDamageAuthorizers.evaluate(
						new StandDamageQuery(null, null)),
				"registered Stand damage authorization was ignored");
		check(calls.get() == 2,
				"Stand damage authorizers did not run in registration order");
		expectIllegalState(() -> StandDamageAuthorizers.register(
				first, query -> true));
		StandDamageAuthorizers.resetForTests();
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
				"duplicate Stand damage authorizer was accepted");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
