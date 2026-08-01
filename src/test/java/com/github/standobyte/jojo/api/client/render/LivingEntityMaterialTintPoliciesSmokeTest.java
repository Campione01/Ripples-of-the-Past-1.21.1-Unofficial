package com.github.standobyte.jojo.api.client.render;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import net.minecraft.resources.ResourceLocation;

public final class LivingEntityMaterialTintPoliciesSmokeTest {
	private LivingEntityMaterialTintPoliciesSmokeTest() {}

	public static void run() {
		LivingEntityMaterialTintPolicies.resetForTests();
		ResourceLocation failedPolicy = id("failed_policy");
		ResourceLocation first = id("first");
		ResourceLocation failedTransform = id("failed_transform");
		ResourceLocation second = id("second");

		LivingEntityMaterialTintPolicies.register(failedPolicy, query -> {
			throw new IllegalStateException("expected policy failure");
		});
		LivingEntityMaterialTintPolicies.register(
				first, query -> original -> original ^ 0x00010000);
		LivingEntityMaterialTintPolicies.register(
				failedTransform, query -> original -> {
					throw new IllegalStateException(
							"expected transform failure");
				});
		LivingEntityMaterialTintPolicies.register(
				second, query -> original -> original ^ 0x00000100);

		check(LivingEntityMaterialTintPolicies.transformForTests(
						new LivingEntityMaterialTintQuery(null, 0.5F),
						0xFF123456)
				== 0xFF133556,
				"living material tints did not compose in order");
		check(LivingEntityMaterialTintPolicies.registeredOwners().equals(
				List.of(failedPolicy, first, failedTransform, second)),
				"living material tint registration order changed");
		expectIllegalState(() -> LivingEntityMaterialTintPolicies.register(
				second, query -> null));

		LivingEntityMaterialTintPolicies.resetForTests();
		check(LivingEntityMaterialTintPolicies.transformForTests(
						new LivingEntityMaterialTintQuery(null, 0.5F),
						0xFFABCDEF)
				== 0xFFABCDEF,
				"empty living material tint registry changed color");
		verifyMixinContract();
	}

	private static void verifyMixinContract() {
		String source = source(
				"src/main/java/com/github/standobyte/jojo/mixin/"
						+ "client/v1_21_1_modelanim/"
						+ "LivingEntityRendererMixin.java");
		check(source.contains("@ModifyVariable("),
				"whole living render tint hook is missing");
		check(source.contains("at = @At(\"HEAD\")"),
				"whole living render tint must wrap at method entry");
		check(source.contains("argsOnly = true"),
				"whole living render tint must replace only the buffer arg");
		check(source.contains("LivingEntityMaterialTintPolicies.wrap("),
				"whole living render tint registry is not wired");
	}

	private static String source(String path) {
		try {
			return Files.readString(Path.of(path));
		}
		catch (IOException exception) {
			throw new AssertionError("Could not read " + path, exception);
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
				"duplicate living material tint policy was accepted");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
