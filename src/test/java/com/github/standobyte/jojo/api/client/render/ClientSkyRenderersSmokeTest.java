package com.github.standobyte.jojo.api.client.render;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.resources.ResourceLocation;

public final class ClientSkyRenderersSmokeTest {
	private ClientSkyRenderersSmokeTest() {}

	public static void run() {
		ClientSkyRenderers.resetForTests();
		ResourceLocation failed = id("failed");
		ResourceLocation inactive = id("inactive");
		ResourceLocation active = id("active");
		ResourceLocation ignored = id("ignored");
		AtomicInteger ignoredCalls = new AtomicInteger();
		AtomicReference<ClientSkyRenderContext> rendered =
				new AtomicReference<>();
		ClientSkyRenderContext context = new ClientSkyRenderContext(
				null, null, null, 0.5F, null, true, () -> {});

		ClientSkyRenderers.register(failed, query -> {
			throw new IllegalStateException("expected provider failure");
		});
		ClientSkyRenderers.register(inactive, query -> null);
		ClientSkyRenderers.register(active, query ->
				new ClientSkyRenderer() {
					@Override
					public void render(ClientSkyRenderContext renderContext) {
						rendered.set(renderContext);
					}

					@Override
					public boolean suppressVanillaClouds() {
						return true;
					}
				});
		ClientSkyRenderers.register(ignored, query -> {
			ignoredCalls.incrementAndGet();
			return renderContext -> {};
		});

		check(ClientSkyRenderers.renderSky(context),
				"successful sky replacement did not claim the render");
		check(rendered.get() == context,
				"sky renderer did not receive the render context");
		check(ClientSkyRenderers.suppressesClouds(null),
				"active sky renderer did not suppress vanilla clouds");
		check(ignoredCalls.get() == 0,
				"provider after the first active renderer was queried");
		check(ClientSkyRenderers.registeredOwners().equals(
				List.of(failed, inactive, active, ignored)),
				"sky renderer registration order changed");
		expectIllegalState(() -> ClientSkyRenderers.register(
				active, query -> null));

		ClientSkyRenderers.resetForTests();
		ClientSkyRenderers.register(id("render_failure"), query ->
				renderContext -> {
					throw new IllegalStateException(
							"expected render failure");
				});
		check(!ClientSkyRenderers.renderSky(context),
				"failed sky replacement suppressed the vanilla sky");

		ClientSkyRenderers.resetForTests();
		ClientSkyRenderers.register(id("cloud_failure"), query ->
				new ClientSkyRenderer() {
					@Override
					public void render(ClientSkyRenderContext renderContext) {}

					@Override
					public boolean suppressVanillaClouds() {
						throw new IllegalStateException(
								"expected cloud failure");
					}
				});
		check(!ClientSkyRenderers.suppressesClouds(null),
				"failed cloud selection did not fail open");

		ClientSkyRenderers.resetForTests();
		check(!ClientSkyRenderers.renderSky(context),
				"empty sky registry suppressed the vanilla sky");
		check(!ClientSkyRenderers.suppressesClouds(null),
				"empty sky registry suppressed vanilla clouds");
		verifyMixinContract();
	}

	private static void verifyMixinContract() {
		String source = source(
				"src/main/java/com/github/standobyte/jojo/mixin/"
						+ "client/render/"
						+ "LevelRendererSkyPresentationMixin.java");
		check(source.contains("@Shadow private ClientLevel level;"),
				"sky hooks must use LevelRenderer's current level");
		check(source.contains("ClientSkyRenderers.renderSky("),
				"sky replacement hook is missing");
		check(source.contains("ClientSkyRenderers.suppressesClouds("),
				"cloud suppression hook is missing");
		check(source.contains("cancellable = true"),
				"sky replacement hooks must be cancellable");
		check(source.contains("ClientSkyPresentationProviders.timeOfDay("),
				"existing sky time-of-day wrapper was removed");
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
				"duplicate sky renderer provider was accepted");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
