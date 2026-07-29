package com.github.standobyte.jojo.api.stonemask;

import java.util.Set;

import net.minecraft.resources.ResourceLocation;

public final class StoneMaskExtensionsSmokeTest {
	private StoneMaskExtensionsSmokeTest() {}

	public static void run() {
		ResourceLocation owner =
				ResourceLocation.fromNamespaceAndPath(
						"rotp_test", "stone_mask");

		StoneMaskExtensions.resetForTests();
		StoneMaskExtensions.registerBlock(owner, () -> null);
		check(StoneMaskExtensions.registeredOwners()
						.equals(Set.of(owner)),
				"Stone Mask owner registration drifted");
		expectFailure(() -> StoneMaskExtensions.registerBlock(
				owner, () -> null));

		Set<ResourceLocation> owners =
				StoneMaskExtensions.registeredOwners();
		expectUnsupported(() -> owners.add(
				ResourceLocation.fromNamespaceAndPath(
						"rotp_test", "mutated")));

		StoneMaskExtensions.resetForTests();
		check(StoneMaskExtensions.resolveBlocks().length == 0,
				"empty Stone Mask block resolution drifted");
		expectFailure(() -> StoneMaskExtensions.registerBlock(
				owner, () -> null));
		StoneMaskExtensions.resetForTests();
	}

	private static void expectFailure(Runnable action) {
		try {
			action.run();
			throw new AssertionError(
					"Expected Stone Mask registration failure");
		}
		catch (IllegalStateException expected) {}
	}

	private static void expectUnsupported(Runnable action) {
		try {
			action.run();
			throw new AssertionError(
					"Expected immutable owner snapshot");
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
