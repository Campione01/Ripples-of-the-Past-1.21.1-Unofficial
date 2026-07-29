package com.github.standobyte.jojo.api.control;

import java.util.List;

import net.minecraft.resources.ResourceLocation;

public final class LivingSwingDurationModifiersSmokeTest {
	private LivingSwingDurationModifiersSmokeTest() {}

	public static void run() {
		LivingSwingDurationModifiers.resetForTests();
		ResourceLocation failed = id("failed");
		ResourceLocation doubleDuration = id("double");
		ResourceLocation addOne = id("add_one");

		LivingSwingDurationModifiers.register(failed, query -> {
			throw new IllegalStateException("expected smoke failure");
		});
		LivingSwingDurationModifiers.register(
				doubleDuration,
				query -> query.currentDuration() * 2);
		LivingSwingDurationModifiers.register(
				addOne,
				query -> query.currentDuration() + 1);

		check(LivingSwingDurationModifiers.apply(null, 6) == 13,
				"swing-duration modifiers did not compose in order");
		check(LivingSwingDurationModifiers.registeredOwners()
						.equals(List.of(
								failed, doubleDuration, addOne)),
				"swing-duration modifier order changed");
		expectIllegalState(() ->
				LivingSwingDurationModifiers.register(
						addOne, query -> 1));
		LivingSwingDurationModifiers.resetForTests();
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
				"duplicate swing-duration modifier was accepted");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
