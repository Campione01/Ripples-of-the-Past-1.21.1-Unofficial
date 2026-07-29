package com.github.standobyte.jojo.api.client.render;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;

public final class ItemMaterialTintPoliciesSmokeTest {
	private ItemMaterialTintPoliciesSmokeTest() {}

	public static void run() {
		ItemMaterialTintPolicies.resetForTests();
		ResourceLocation failed = id("failed");
		ResourceLocation tint = id("tint");
		AtomicInteger calls = new AtomicInteger();

		ItemMaterialTintPolicies.register(failed, query -> {
			calls.incrementAndGet();
			throw new IllegalStateException("expected smoke failure");
		});
		ItemMaterialTintPolicies.register(tint, query -> {
			calls.incrementAndGet();
			return original -> original ^ 0x00010203;
		});

		check(ItemMaterialTintPolicies.transformForTests(
						new ItemMaterialTintQuery(
								null, ItemDisplayContext.FIXED),
						0xFF123456)
				== 0xFF133655,
				"item material tint was not applied");
		check(calls.get() == 2,
				"item tint policies did not run in registration order");
		check(ItemMaterialTintPolicies.registeredOwners()
						.equals(List.of(failed, tint)),
				"item tint policy order changed");
		expectIllegalState(() -> ItemMaterialTintPolicies.register(
				tint, query -> null));
		ItemMaterialTintPolicies.resetForTests();
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
				"duplicate item tint registration was accepted");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
