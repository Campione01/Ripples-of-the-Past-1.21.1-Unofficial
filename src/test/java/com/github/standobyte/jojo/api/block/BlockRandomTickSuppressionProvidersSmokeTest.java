package com.github.standobyte.jojo.api.block;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.resources.ResourceLocation;

public final class BlockRandomTickSuppressionProvidersSmokeTest {
	private BlockRandomTickSuppressionProvidersSmokeTest() {}

	public static void run() {
		BlockRandomTickSuppressionProviders.resetForTests();
		ResourceLocation failed = id("failed");
		ResourceLocation active = id("active");
		AtomicInteger calls = new AtomicInteger();

		BlockRandomTickSuppressionProviders.register(failed, query -> {
			calls.incrementAndGet();
			throw new IllegalStateException("expected smoke failure");
		});
		BlockRandomTickSuppressionProviders.register(active, query -> {
			calls.incrementAndGet();
			return true;
		});

		check(BlockRandomTickSuppressionProviders.shouldSuppress(
						new BlockRandomTickSuppressionQuery(
								null, null, null)),
				"provider failure prevented random-tick suppression");
		check(calls.get() == 2,
				"random-tick providers did not run in order");
		check(BlockRandomTickSuppressionProviders.registeredOwners()
						.equals(List.of(failed, active)),
				"random-tick provider order changed");
		expectIllegalState(() ->
				BlockRandomTickSuppressionProviders.register(
						active, query -> false));
		BlockRandomTickSuppressionProviders.resetForTests();
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
				"duplicate random-tick provider was accepted");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
