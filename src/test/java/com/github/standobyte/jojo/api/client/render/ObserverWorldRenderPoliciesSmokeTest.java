package com.github.standobyte.jojo.api.client.render;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.github.standobyte.jojo.api.client.render.ObserverWorldRenderPolicies.FrameScope;
import com.github.standobyte.jojo.api.client.render.ObserverWorldRenderPolicyProvider.Pass;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.resources.ResourceLocation;

public final class ObserverWorldRenderPoliciesSmokeTest {
	private ObserverWorldRenderPoliciesSmokeTest() {}

	public static void run() {
		verifyRegistryUnionAndFailureIsolation();
		verifyStackedImmutableScopes();
		verifyGuardCallPathsAndClientOnlyLoading();
	}

	private static void verifyRegistryUnionAndFailureIsolation() {
		ObserverWorldRenderPolicies.resetForTests();
		check(List.of(Pass.values()).equals(List.of(
				Pass.TERRAIN,
				Pass.WEATHER,
				Pass.PARTICLES,
				Pass.BLOCK_ENTITIES)),
				"observer world render pass enum drifted");

		List<String> calls = new ArrayList<>();
		ResourceLocation terrain = id("terrain");
		ResourceLocation failing = id("failing");
		ResourceLocation absent = id("absent");
		ResourceLocation weatherAndBlocks =
				id("weather_and_blocks");
		ObserverWorldRenderPolicies.register(
				terrain,
				context -> {
					calls.add("terrain");
					return Set.of(Pass.TERRAIN);
				});
		ObserverWorldRenderPolicies.register(
				failing,
				context -> {
					calls.add("failing");
					throw new IllegalStateException(
							"isolated test failure");
				});
		ObserverWorldRenderPolicies.register(
				absent,
				context -> {
					calls.add("absent");
					return null;
				});
		ObserverWorldRenderPolicies.register(
				weatherAndBlocks,
				context -> {
					calls.add("weather_and_blocks");
					return Set.of(
							Pass.WEATHER,
							Pass.BLOCK_ENTITIES);
				});

		Set<Pass> resolved =
				ObserverWorldRenderPolicies.resolve(null);
		check(resolved.equals(Set.of(
				Pass.TERRAIN,
				Pass.WEATHER,
				Pass.BLOCK_ENTITIES)),
				"provider results were not unioned");
		check(!resolved.contains(Pass.PARTICLES),
				"failed provider suppressed a pass");
		check(calls.equals(List.of(
				"terrain",
				"failing",
				"absent",
				"weather_and_blocks")),
				"providers lost owner registration order");
		check(ObserverWorldRenderPolicies.registeredOwners()
						.equals(List.of(
								terrain,
								failing,
								absent,
								weatherAndBlocks)),
				"render policy owner order changed");
		expectIllegalState(() ->
				ObserverWorldRenderPolicies.register(
						terrain,
						context -> Set.of()));
		expectUnsupported(() -> resolved.add(Pass.PARTICLES));

		int callsAfterResolve = calls.size();
		try (FrameScope ignored =
				ObserverWorldRenderPolicies.pushSnapshot(resolved)) {
			check(ObserverWorldRenderPolicies
						.suppresses(Pass.TERRAIN)
					&& ObserverWorldRenderPolicies
							.suppresses(Pass.WEATHER)
					&& !ObserverWorldRenderPolicies
							.suppresses(Pass.PARTICLES),
					"active frame did not use its resolved snapshot");
			ObserverWorldRenderPolicies
					.suppresses(Pass.BLOCK_ENTITIES);
		}
		check(calls.size() == callsAfterResolve,
				"pass guards re-evaluated providers within one frame");
		ObserverWorldRenderPolicies.resetForTests();
	}

	private static void verifyStackedImmutableScopes() {
		ObserverWorldRenderPolicies.resetForTests();
		check(!ObserverWorldRenderPolicies
						.suppresses(Pass.TERRAIN),
				"render policy leaked before a frame");

		EnumSet<Pass> mutable = EnumSet.of(Pass.TERRAIN);
		try (FrameScope outer =
				ObserverWorldRenderPolicies.pushSnapshot(mutable)) {
			mutable.clear();
			check(ObserverWorldRenderPolicies
						.suppresses(Pass.TERRAIN),
					"active frame snapshot was mutable");
			try (FrameScope inner =
					ObserverWorldRenderPolicies.pushSnapshot(
							Set.of(Pass.WEATHER))) {
				check(ObserverWorldRenderPolicies
							.suppresses(Pass.WEATHER)
						&& !ObserverWorldRenderPolicies
								.suppresses(Pass.TERRAIN),
						"nested frame did not isolate its snapshot");
			}
			check(ObserverWorldRenderPolicies
						.suppresses(Pass.TERRAIN)
					&& !ObserverWorldRenderPolicies
							.suppresses(Pass.WEATHER),
					"nested frame did not restore its parent");
			outer.close();
		}
		check(!ObserverWorldRenderPolicies
						.suppresses(Pass.TERRAIN),
				"closed frame leaked into a later world");

		try {
			try (FrameScope ignored =
					ObserverWorldRenderPolicies.pushSnapshot(
							Set.of(Pass.PARTICLES))) {
				throw new ExpectedFailure();
			}
		}
		catch (ExpectedFailure expected) {
			// Expected.
		}
		check(!ObserverWorldRenderPolicies
						.suppresses(Pass.PARTICLES),
				"exceptional frame exit leaked its snapshot");
		ObserverWorldRenderPolicies.resetForTests();
	}

	private static void verifyGuardCallPathsAndClientOnlyLoading() {
		Path root = Path.of(System.getProperty("user.dir"));
		String registry = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/api/"
				+ "client/render/ObserverWorldRenderPolicies.java"));
		String beginFrame = between(
				registry,
				"public static FrameScope beginFrame",
				"public static boolean suppresses");
		check(count(beginFrame, "resolve(context)") == 1,
				"frame scope does not resolve providers exactly once");

		String provider = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/api/"
				+ "client/render/"
				+ "ObserverWorldRenderPolicyProvider.java"));
		check(provider.contains("ClientLevel level")
				&& provider.contains("LocalPlayer observer")
				&& provider.contains("Entity cameraEntity")
				&& provider.contains("Camera camera")
				&& provider.contains("long gameTime")
				&& provider.contains("float partialTick"),
				"observer render context fields drifted");

		String levelMixin = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/mixin/"
				+ "client/render/ObserverWorldRenderLevelMixin.java"));
		check(levelMixin.contains(
				"@WrapMethod(method = \"renderLevel\")")
				&& levelMixin.contains("try (FrameScope ignored")
				&& levelMixin.contains("original.call("),
				"renderLevel scope is not protected by finally cleanup");
		check(levelMixin.contains(
				"method = \"renderSectionLayer\"")
				&& levelMixin.contains("Pass.TERRAIN")
				&& levelMixin.contains(
						"method = \"renderSnowAndRain\"")
				&& levelMixin.contains("Pass.WEATHER"),
				"terrain or weather guard is missing");
		check(!levelMixin.contains("renderSky")
				&& !levelMixin.contains("renderClouds")
				&& !levelMixin.contains("renderEntity"),
				"policy mixin suppresses an undeclared world pass");

		String particles = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/mixin/"
				+ "client/render/"
				+ "ObserverWorldRenderParticleMixin.java"));
		check(particles.contains(
				"Ljava/util/function/Predicate;)V")
				&& particles.contains("Pass.PARTICLES")
				&& !particles.contains("method = \"tick\""),
				"particle guard is not limited to the draw overload");

		String blockEntities = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/mixin/"
				+ "client/render/"
				+ "ObserverWorldRenderBlockEntityMixin.java"));
		check(blockEntities.contains(
				"BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;")
				&& blockEntities.contains("Pass.BLOCK_ENTITIES")
				&& !blockEntities.contains("renderItem"),
				"block-entity guard affects the wrong render path");

		verifyClientOnlyMixinRouting(root);
		verifyMainSourceReferenceBoundary(root);
	}

	private static void verifyClientOnlyMixinRouting(Path root) {
		JsonObject mixins = JsonParser.parseString(read(root.resolve(
				"src/main/resources/jojo_ripples.mixins.json")))
				.getAsJsonObject();
		JsonArray common = mixins.getAsJsonArray("mixins");
		JsonArray client = mixins.getAsJsonArray("client");
		for (String name : List.of(
				"client.render.ObserverWorldRenderLevelMixin",
				"client.render.ObserverWorldRenderParticleMixin",
				"client.render.ObserverWorldRenderBlockEntityMixin")) {
			check(contains(client, name),
					name + " is not client-scoped");
			check(!contains(common, name),
					name + " polluted dedicated-server mixins");
		}
	}

	private static void verifyMainSourceReferenceBoundary(Path root) {
		Path sourceRoot = root.resolve("src/main/java");
		try (var paths = Files.walk(sourceRoot)) {
			List<String> invalid = paths
					.filter(path -> path.toString().endsWith(".java"))
					.filter(path -> read(path).contains(
							"ObserverWorldRender"))
					.map(sourceRoot::relativize)
					.map(Path::toString)
					.map(path -> path.replace('\\', '/'))
					.filter(path -> !path.startsWith(
							"com/github/standobyte/jojo/api/"
							+ "client/render/"))
					.filter(path -> !path.startsWith(
							"com/github/standobyte/jojo/mixin/"
							+ "client/render/"))
					.filter(path -> !path.equals(
							"com/github/standobyte/jojo/api/"
							+ "RotpAddonApi.java"))
					.toList();
			check(invalid.isEmpty(),
					"client render policy leaked into common source: "
							+ invalid);
		}
		catch (IOException error) {
			throw new AssertionError(
					"failed to scan main source references", error);
		}

		String addonApi = read(sourceRoot.resolve(
				"com/github/standobyte/jojo/api/RotpAddonApi.java"));
		check(!addonApi.contains("net.minecraft.client")
				&& !addonApi.contains(
						"import com.github.standobyte.jojo.api."
						+ "client.render"),
				"feature negotiation loads client render classes");
	}

	private static boolean contains(JsonArray array, String value) {
		for (var element : array) {
			if (value.equals(element.getAsString())) {
				return true;
			}
		}
		return false;
	}

	private static int count(String source, String token) {
		int count = 0;
		int index = 0;
		while ((index = source.indexOf(token, index)) >= 0) {
			count++;
			index += token.length();
		}
		return count;
	}

	private static String between(
			String source, String startToken, String endToken) {
		int start = source.indexOf(startToken);
		int end = source.indexOf(endToken, start);
		if (start < 0 || end < 0 || end <= start) {
			throw new AssertionError(
					"failed to locate source contract between "
							+ startToken + " and " + endToken);
		}
		return source.substring(start, end);
	}

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(
				"rotp_test", path);
	}

	private static String read(Path path) {
		try {
			return Files.readString(path);
		}
		catch (IOException error) {
			throw new AssertionError("failed to read " + path, error);
		}
	}

	private static void expectIllegalState(Runnable action) {
		try {
			action.run();
		}
		catch (IllegalStateException expected) {
			return;
		}
		throw new AssertionError(
				"duplicate render policy owner was accepted");
	}

	private static void expectUnsupported(Runnable action) {
		try {
			action.run();
		}
		catch (UnsupportedOperationException expected) {
			return;
		}
		throw new AssertionError(
				"resolved render policy snapshot was mutable");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static final class ExpectedFailure
			extends RuntimeException {
		private static final long serialVersionUID = 1L;
	}
}
