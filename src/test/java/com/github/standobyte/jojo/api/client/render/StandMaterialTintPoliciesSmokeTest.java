package com.github.standobyte.jojo.api.client.render;

import java.util.List;

import net.minecraft.resources.ResourceLocation;

public final class StandMaterialTintPoliciesSmokeTest {
	private StandMaterialTintPoliciesSmokeTest() {}

	public static void run() {
		StandMaterialTintPolicies.resetForTests();
		ResourceLocation failed = id("failed");
		ResourceLocation first = id("first");
		ResourceLocation second = id("second");

		StandMaterialTintPolicies.register(failed, query -> {
			throw new IllegalStateException("expected smoke failure");
		});
		StandMaterialTintPolicies.register(
				first, query -> original -> original ^ 0x00010000);
		StandMaterialTintPolicies.register(
				second, query -> original -> original ^ 0x00000100);

		check(StandMaterialTintPolicies.transformForTests(
						new StandMaterialTintQuery(null, 0.5F),
						0xFF123456)
				== 0xFF133556,
				"Stand material tints did not compose in order");
		check(StandMaterialTintPolicies.registeredOwners()
						.equals(List.of(failed, first, second)),
				"Stand material tint order changed");
		expectIllegalState(() ->
				StandMaterialTintPolicies.register(
						second, query -> null));
		StandMaterialTintPolicies.resetForTests();
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
				"duplicate Stand material tint was accepted");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
