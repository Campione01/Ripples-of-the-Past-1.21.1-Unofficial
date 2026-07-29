package com.github.standobyte.jojo.api.client.render;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.ResourceLocation;

public final class EntityPostRenderExtensionsSmokeTest {
	private EntityPostRenderExtensionsSmokeTest() {}

	public static void main(String[] args) {
		run();
	}

	public static void run() {
		List<String> calls = new ArrayList<>();
		List<ResourceLocation> failures = new ArrayList<>();
		ResourceLocation first =
				ResourceLocation.fromNamespaceAndPath("test", "first");
		ResourceLocation failing =
				ResourceLocation.fromNamespaceAndPath("test", "failing");
		ResourceLocation last =
				ResourceLocation.fromNamespaceAndPath("test", "last");

		try (EntityPostRenderExtensions.Registration ignoredLast =
						EntityPostRenderExtensions.register(
								last, 20, context -> calls.add("last"));
				EntityPostRenderExtensions.Registration ignoredFirst =
						EntityPostRenderExtensions.register(
								first, 0, context -> calls.add("first"));
				EntityPostRenderExtensions.Registration ignoredFailing =
						EntityPostRenderExtensions.register(
								failing,
								10,
								new EntityPostRenderExtension() {
									@Override
									public void afterEntityRender(
											EntityPostRenderContext context) {
										calls.add("failing-render");
										throw new IllegalStateException(
												"render failure");
									}

									@Override
									public void endFrame(long frameId) {
										calls.add("failing-frame-" + frameId);
										throw new IllegalStateException(
												"frame failure");
									}
								})) {
			boolean duplicateRejected = false;
			try {
				EntityPostRenderExtensions.register(
						first, 99, context -> {});
			}
			catch (IllegalStateException expected) {
				duplicateRejected = true;
			}
			check(duplicateRejected, "duplicate owner was accepted");

			EntityPostRenderExtensions.dispatchForTest(
					null, (owner, throwable) -> failures.add(owner));
			check(calls.equals(List.of(
							"first", "failing-render", "last")),
					"render order or exception isolation drifted: " + calls);
			check(failures.equals(List.of(failing)),
					"render failure owner was not isolated: " + failures);

			EntityPostRenderExtensions.finishFrameForTest(
					(owner, throwable) -> failures.add(owner));
			check(EntityPostRenderExtensions.currentFrameId() == 1L,
					"frame ID did not advance exactly once");
			check(calls.contains("failing-frame-0"),
					"end-frame callback did not receive completed frame ID");
			check(failures.equals(List.of(failing, failing)),
					"end-frame failure was not isolated: " + failures);
		}

		calls.clear();
		EntityPostRenderExtensions.dispatchForTest(
				null, (owner, throwable) -> failures.add(owner));
		check(calls.isEmpty(), "closed registrations remained active");
		verifyMixinBoundary();
	}

	private static void verifyMixinBoundary() {
		Path path = Path.of(
				System.getProperty("user.dir"),
				"src/main/java/com/github/standobyte/jojo/mixin/client/"
						+ "render/EntityRenderDispatcherExtensionMixin.java");
		String source;
		try {
			source = Files.readString(path);
		}
		catch (IOException error) {
			throw new AssertionError("failed to read " + path, error);
		}
		check(source.contains("EntityRenderer;render(")
						&& source.contains("shift = At.Shift.AFTER"),
				"post-entity hook no longer follows the registered renderer");
		check(!source.contains("cancellable = true")
						&& !source.contains("ci.cancel()"),
				"post-entity hook must not cancel unrelated rendering");
		check(source.contains(
						"EntityPostRenderExtensions.afterEntityRender("),
				"post-entity hook no longer dispatches the public API");
		check(source.contains(
						"EntityMaskPostEffect.isCapturePass()"),
				"mask capture can recursively dispatch post-entity addons");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
