package com.github.standobyte.jojo.api.client.render;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.ResourceLocation;

public final class LivingEntityRenderLayerExtensionsSmokeTest {
	private LivingEntityRenderLayerExtensionsSmokeTest() {}

	public static void run() {
		List<String> calls = new ArrayList<>();
		List<ResourceLocation> failures = new ArrayList<>();
		ResourceLocation first = id("first");
		ResourceLocation failing = id("failing");
		ResourceLocation tied = id("tied");
		ResourceLocation last = id("last");

		try (LivingEntityRenderLayerExtensions.Registration ignoredLast =
						LivingEntityRenderLayerExtensions.register(
								last, 20, context -> calls.add("last"));
				LivingEntityRenderLayerExtensions.Registration ignoredTied =
						LivingEntityRenderLayerExtensions.register(
								tied, 0, context -> calls.add("tied"));
				LivingEntityRenderLayerExtensions.Registration ignoredFirst =
						LivingEntityRenderLayerExtensions.register(
								first, 0, context -> calls.add("first"));
				LivingEntityRenderLayerExtensions.Registration ignoredFailing =
						LivingEntityRenderLayerExtensions.register(
								failing, 10, context -> {
									calls.add("failing");
									throw new IllegalStateException(
											"expected smoke failure");
								})) {
			boolean duplicateRejected = false;
			try {
				LivingEntityRenderLayerExtensions.register(
						first, 99, context -> {});
			}
			catch (IllegalStateException expected) {
				duplicateRejected = true;
			}
			check(duplicateRejected, "duplicate owner was accepted");

			LivingEntityRenderLayerExtensions.dispatchForTest(
					null, (owner, throwable) -> failures.add(owner));
			check(calls.equals(List.of(
							"first", "tied", "failing", "last")),
					"order or exception isolation drifted: " + calls);
			check(failures.equals(List.of(failing)),
					"failure was not attributed to its owner");
		}

		calls.clear();
		LivingEntityRenderLayerExtensions.dispatchForTest(
				null, (owner, throwable) -> failures.add(owner));
		check(calls.isEmpty(), "closed registrations remained active");
		verifyMixinBoundary();
	}

	private static void verifyMixinBoundary() {
		Path path = Path.of(
				System.getProperty("user.dir"),
				"src/main/java/com/github/standobyte/jojo/mixin/client/"
						+ "render/LivingEntityRenderLayerExtensionMixin.java");
		String source;
		try {
			source = Files.readString(path);
		}
		catch (IOException error) {
			throw new AssertionError("failed to read " + path, error);
		}
		check(source.contains("PoseStack;popPose()V")
				&& source.contains("shift = At.Shift.BEFORE"),
				"living layer hook no longer runs before pose restoration");
		check(source.contains("!entity.isSpectator()"),
				"living layer hook no longer matches spectator semantics");
		check(source.contains(
				"LivingEntityRenderLayerExtensions"
						+ ".renderAfterVanillaLayers("),
				"living layer hook no longer dispatches the public API");
		check(source.contains(
						"EntityMaskPostEffect.isCapturePass()"),
				"mask capture can recursively dispatch living addon layers");
	}

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath("rotp_test", path);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
