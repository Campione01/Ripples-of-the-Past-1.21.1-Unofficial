package com.github.standobyte.jojo.api.control;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.resources.ResourceLocation;

public final class CreeperFuseSuppressionProvidersSmokeTest {
	private CreeperFuseSuppressionProvidersSmokeTest() {}

	public static void run() {
		CreeperFuseSuppressionProviders.resetForTests();
		ResourceLocation failed = id("failed");
		ResourceLocation active = id("active");
		AtomicInteger calls = new AtomicInteger();

		CreeperFuseSuppressionProviders.register(failed, creeper -> {
			calls.incrementAndGet();
			throw new IllegalStateException("expected smoke failure");
		});
		CreeperFuseSuppressionProviders.register(active, creeper -> {
			calls.incrementAndGet();
			return true;
		});

		check(CreeperFuseSuppressionProviders.shouldSuppress(null),
				"provider failure prevented later Creeper suppression");
		check(calls.get() == 2,
				"Creeper providers did not run in registration order");
		check(CreeperFuseSuppressionProviders.registeredOwners()
						.equals(List.of(failed, active)),
				"Creeper provider order changed");
		expectIllegalState(() ->
				CreeperFuseSuppressionProviders.register(
						active, creeper -> false));
		CreeperFuseSuppressionProviders.resetForTests();
	}

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath("rotp_test", path);
	}

	private static void expectIllegalState(Runnable action) {
		try {
			action.run();
		}
		catch (IllegalStateException expected) {
			return;
		}
		throw new AssertionError(
				"duplicate Creeper provider registration was accepted");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
