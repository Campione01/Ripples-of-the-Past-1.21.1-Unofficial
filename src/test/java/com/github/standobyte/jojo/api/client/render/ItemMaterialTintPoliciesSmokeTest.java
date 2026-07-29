package com.github.standobyte.jojo.api.client.render;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
		verifyMixinContract();
	}

	private static void verifyMixinContract() {
		String source = source(
				"src/main/java/com/github/standobyte/jojo/mixin/"
						+ "client/ItemRendererMixin.java");
		check(source.contains(
				"@Local(argsOnly = true) ItemStack itemStack"),
				"item tint must capture ItemStack through MixinExtras");
		check(source.contains(
				"@Local(argsOnly = true)\n"
						+ "\t\t\tItemDisplayContext displayContext"),
				"item tint must capture display context through MixinExtras");
		check(!source.contains(
				"VertexConsumer original,\n"
						+ "\t\t\tItemStack itemStack,"),
				"item tint @ModifyArg must use single-argument mode");
	}

	private static String source(String path) {
		try {
			return Files.readString(Path.of(path));
		}
		catch (IOException exception) {
			throw new AssertionError("Could not read " + path,
					exception);
		}
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
