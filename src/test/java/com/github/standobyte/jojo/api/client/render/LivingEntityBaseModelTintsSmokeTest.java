package com.github.standobyte.jojo.api.client.render;

import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.resources.ResourceLocation;

public final class LivingEntityBaseModelTintsSmokeTest {
	private LivingEntityBaseModelTintsSmokeTest() {}

	public static void run() {
		LivingEntityBaseModelTints.resetForTests();
		ResourceLocation failed = id("failed");
		ResourceLocation tint = id("tint");
		AtomicInteger calls = new AtomicInteger();

		LivingEntityBaseModelTints.register(failed, query -> {
			calls.incrementAndGet();
			throw new IllegalStateException("expected smoke failure");
		});
		LivingEntityBaseModelTints.register(tint, query -> {
			calls.incrementAndGet();
			return OptionalInt.of(0xFF123456);
		});

		check(LivingEntityBaseModelTints.apply(
						null, null, 0.5F, 0xFFFFFFFF)
				== 0xFF123456,
				"living base-model tint was not applied");
		check(calls.get() == 2,
				"living tint providers did not run in registration order");
		check(LivingEntityBaseModelTints.registeredOwners()
						.equals(List.of(failed, tint)),
				"living tint provider order changed");
		expectIllegalState(() -> LivingEntityBaseModelTints.register(
				tint, query -> OptionalInt.empty()));
		LivingEntityBaseModelTints.resetForTests();
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
				"duplicate living tint registration was accepted");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
