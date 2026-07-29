package com.github.standobyte.jojo.api.client.time;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.github.standobyte.jojo.mixin.client.time.ParticleEngineRegionalTimeDilationMixinSmokeTest;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public final class ClientRegionalTimeDilationPoliciesSmokeTest {
	private ClientRegionalTimeDilationPoliciesSmokeTest() {}

	public static void main(String[] args) {
		run();
	}

	public static void run() {
		verifyOwnerRegistryAndMinimumFactor();
		verifyProviderFailureEpisodes();
		verifyFractionalTickCadence();
		ParticleEngineRegionalTimeDilationMixinSmokeTest.run();
		verifyClientHookBoundaries();
	}

	private static void verifyProviderFailureEpisodes() {
		var firstOwner =
				new ClientRegionalTimeDilationPolicies.ProviderFailureState();
		var secondOwner =
				new ClientRegionalTimeDilationPolicies.ProviderFailureState();
		check(firstOwner.recordFailure(),
				"first provider failure was not reportable");
		check(!firstOwner.recordFailure()
						&& !firstOwner.recordFailure(),
				"continuous provider failure was not rate-limited");
		check(secondOwner.recordFailure(),
				"failure state leaked between provider owners");
		firstOwner.recordSuccess();
		check(firstOwner.recordFailure(),
				"provider failure after recovery was not reportable");

		ClientRegionalTimeDilationPolicies.resetForTests();
		FailureMode[] mode = { FailureMode.INVALID };
		ResourceLocation owner = id("failure_episode");
		ClientRegionalTimeDilationPolicies.register(
				owner,
				query -> switch (mode[0]) {
					case INVALID -> Float.NaN;
					case EXCEPTION -> throw new IllegalStateException(
							"expected failure episode");
					case VALID -> 0.5F;
				});
		RecordingFailureReporter reports =
				new RecordingFailureReporter();
		ClientRegionalTimeDilationQuery query =
				new ClientRegionalTimeDilationQuery(
						ClientRegionalTimeDilationSurface.PARTICLE,
						Vec3.ZERO,
						null);

		ClientRegionalTimeDilationPolicies.resolve(query, reports);
		ClientRegionalTimeDilationPolicies.resolve(query, reports);
		check(reports.invalidFactors == 1
						&& reports.exceptions == 0,
				"repeated invalid factors were not one failure episode");

		mode[0] = FailureMode.EXCEPTION;
		ClientRegionalTimeDilationPolicies.resolve(query, reports);
		check(reports.invalidFactors == 1
						&& reports.exceptions == 0,
				"invalid result and exception used separate episodes");

		mode[0] = FailureMode.VALID;
		check(ClientRegionalTimeDilationPolicies.resolve(query, reports)
						== 0.5F,
				"valid provider result did not recover normal resolution");
		mode[0] = FailureMode.INVALID;
		ClientRegionalTimeDilationPolicies.resolve(query, reports);
		ClientRegionalTimeDilationPolicies.resolve(query, reports);
		check(reports.invalidFactors == 2
						&& reports.exceptions == 0,
				"invalid factor after recovery was not reported once");

		mode[0] = FailureMode.VALID;
		ClientRegionalTimeDilationPolicies.resolve(query, reports);
		mode[0] = FailureMode.EXCEPTION;
		ClientRegionalTimeDilationPolicies.resolve(query, reports);
		ClientRegionalTimeDilationPolicies.resolve(query, reports);
		check(reports.invalidFactors == 2
						&& reports.exceptions == 1,
				"exception after recovery was not reported once");
		ClientRegionalTimeDilationPolicies.resetForTests();
	}

	private static void verifyOwnerRegistryAndMinimumFactor() {
		ClientRegionalTimeDilationPolicies.resetForTests();
		check(List.of(ClientRegionalTimeDilationSurface.values())
				.equals(List.of(
						ClientRegionalTimeDilationSurface.TIMER,
						ClientRegionalTimeDilationSurface.PARTICLE,
						ClientRegionalTimeDilationSurface.SOUND)),
				"regional time-dilation surfaces drifted");

		List<String> calls = new ArrayList<>();
		ResourceLocation slow = id("z_slow");
		ResourceLocation failing = id("m_failing");
		ResourceLocation invalid = id("n_invalid");
		ResourceLocation slower = id("a_slower");
		var slowRegistration =
				ClientRegionalTimeDilationPolicies.register(
						slow,
						query -> {
							calls.add("slow");
							return 0.5F;
						});
		ClientRegionalTimeDilationPolicies.register(
				failing,
				query -> {
					calls.add("failing");
					throw new IllegalStateException(
							"isolated test failure");
				});
		ClientRegionalTimeDilationPolicies.register(
				invalid,
				query -> {
					calls.add("invalid");
					return Float.NaN;
				});
		ClientRegionalTimeDilationPolicies.register(
				slower,
				query -> {
					calls.add("slower");
					check(query.surface()
									== ClientRegionalTimeDilationSurface.TIMER
							&& query.position().equals(Vec3.ZERO)
							&& query.localPlayer() == null,
							"regional query context changed");
					return 0.25F;
				});

		float resolved = ClientRegionalTimeDilationPolicies.resolve(
				new ClientRegionalTimeDilationQuery(
						ClientRegionalTimeDilationSurface.TIMER,
						Vec3.ZERO,
						null));
		check(resolved == 0.25F,
				"providers did not combine by minimum factor");
		check(calls.equals(List.of(
				"slower", "failing", "invalid", "slow")),
				"providers did not resolve in owner-id order");
		check(ClientRegionalTimeDilationPolicies.registeredOwners()
				.equals(List.of(slower, failing, invalid, slow)),
				"owner snapshot order changed");
		expectIllegalState(() ->
				ClientRegionalTimeDilationPolicies.register(
						slow,
						query -> 1.0F));

		slowRegistration.close();
		check(!ClientRegionalTimeDilationPolicies.registeredOwners()
						.contains(slow),
				"closed regional provider remained registered");
		ClientRegionalTimeDilationPolicies.resetForTests();
		check(ClientRegionalTimeDilationPolicies.resolve(
				new ClientRegionalTimeDilationQuery(
						ClientRegionalTimeDilationSurface.SOUND,
						Vec3.ZERO,
						null)) == 1.0F,
				"empty registry did not restore vanilla timing");
	}

	private static void verifyFractionalTickCadence() {
		ClientRegionalTimeDilationTickAccumulator halfSpeed =
				new ClientRegionalTimeDilationTickAccumulator();
		check(!halfSpeed.advance(0.5F)
				&& halfSpeed.advance(0.5F)
				&& !halfSpeed.advance(0.5F)
				&& halfSpeed.advance(0.5F),
				"half-speed particle cadence is not deterministic");

		ClientRegionalTimeDilationTickAccumulator changingFactor =
				new ClientRegionalTimeDilationTickAccumulator();
		check(!changingFactor.advance(0.25F)
				&& !changingFactor.advance(0.5F)
				&& changingFactor.advance(0.25F),
				"particle tick budget did not survive factor changes");

		ClientRegionalTimeDilationTickAccumulator vanillaReset =
				new ClientRegionalTimeDilationTickAccumulator();
		check(!vanillaReset.advance(0.75F)
				&& vanillaReset.advance(1.0F)
				&& !vanillaReset.advance(0.25F),
				"vanilla factor did not tick immediately and reset budget");

		ClientRegionalTimeDilationTickAccumulator firstParticle =
				new ClientRegionalTimeDilationTickAccumulator();
		ClientRegionalTimeDilationTickAccumulator secondParticle =
				new ClientRegionalTimeDilationTickAccumulator();
		check(!firstParticle.advance(0.25F)
				&& !secondParticle.advance(0.5F)
				&& secondParticle.advance(0.5F)
				&& !firstParticle.advance(0.25F)
				&& firstParticle.advance(0.5F),
				"particle tick accumulators leaked state");

		ClientRegionalTimeDilationTickAccumulator decimalFactor =
				new ClientRegionalTimeDilationTickAccumulator();
		int logicalTicks = 0;
		for (int realTicks = 0; realTicks < 1000; realTicks++) {
			if (decimalFactor.advance(0.1F)) {
				logicalTicks++;
			}
		}
		check(logicalTicks == 100,
				"decimal particle factor drifted from its bounded cadence");

		for (float invalid : new float[] {
				0.0F,
				-0.1F,
				1.01F,
				Float.NaN,
				Float.POSITIVE_INFINITY
		}) {
			expectIllegalArgument(() ->
					new ClientRegionalTimeDilationTickAccumulator()
							.advance(invalid));
		}
	}

	private static void verifyClientHookBoundaries() {
		Path root = Path.of(System.getProperty("user.dir"));
		String timer = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/mixin/"
				+ "client/time/"
				+ "DeltaTrackerTimerRegionalTimeDilationMixin.java"));
		check(timer.contains("@Mixin(DeltaTracker.Timer.class)")
				&& timer.contains("method = \"advanceGameTime\"")
				&& timer.contains("FloatUnaryOperator;apply(F)F")
				&& timer.contains(
						"targetMillisecondsPerTick / factor"),
				"timer hook no longer wraps the target MSPT result");
		check(!timer.contains("java.lang.reflect")
				&& !timer.contains("setAccessible"),
				"timer hook reintroduced reflection");

		String particles = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/mixin/"
				+ "client/time/"
				+ "ParticleEngineRegionalTimeDilationMixin.java"));
		check(particles.contains("method = \"tickParticle\"")
				&& particles.contains("at = @At(\"HEAD\")")
				&& particles.contains("cancellable = true")
				&& particles.contains("new WeakHashMap<>()")
				&& particles.contains("computeIfAbsent")
				&& particles.contains("accumulator.advance(factor)")
				&& particles.contains(
						"jojo_ripples$stabilizeSkippedTick")
				&& particles.contains("ci.cancel()"),
				"particle hook no longer owns fractional tick scheduling");
		check(particles.contains("method = \"clearParticles\"")
				&& particles.contains(
						"jojo_ripples$regionalTickAccumulators.clear()")
				&& particles.contains("!particle.isAlive()"),
				"particle scheduler lifecycle cleanup is incomplete");
		check(!particles.contains("* factor")
				&& !particles.contains("Velocity")
				&& !particles.contains("method = \"render")
				&& !particles.contains("Framebuffer"),
				"particle scheduler reintroduced damping or render hooks");
		check(!particles.contains("ParticleEngine.class.get")
				&& !particles.contains("java.lang.reflect"),
				"particle hook reintroduced reflective engine access");

		String sounds = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/client/"
				+ "sound/ClientsideSoundsHelper.java"));
		check(count(sounds, "applyRegionalTimeDilation(event);") == 1
				&& sounds.contains(
						"ClientRegionalTimeDilationSurface.SOUND")
				&& sounds.contains("sound.getPitch() * factor"),
				"sound hook no longer owns one PlaySoundEvent pitch path");

		JsonObject mixins = JsonParser.parseString(read(root.resolve(
				"src/main/resources/jojo_ripples.mixins.json")))
				.getAsJsonObject();
		JsonArray common = mixins.getAsJsonArray("mixins");
		JsonArray client = mixins.getAsJsonArray("client");
		for (String name : List.of(
				"client.time.AbstractSoundInstanceRegionalTimeDilationAccessor",
				"client.time.DeltaTrackerTimerRegionalTimeDilationMixin",
				"client.time.ParticleEngineRegionalTimeDilationMixin",
				"client.time."
						+ "ParticleRegionalTimeDilationInterpolationAccessor")) {
			check(contains(client, name),
					name + " is not client-scoped");
			check(!contains(common, name),
					name + " polluted dedicated-server mixins");
		}
		check(!contains(
						client,
						"client.time.ParticleRegionalTimeDilationAccessor"),
				"obsolete particle velocity accessor is still mixed in");
		check(Files.notExists(root.resolve(
						"src/main/java/com/github/standobyte/jojo/mixin/"
						+ "client/time/"
						+ "ParticleRegionalTimeDilationAccessor.java")),
				"obsolete particle velocity accessor source still exists");
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

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(
				"rotp_test", path);
	}

	private enum FailureMode {
		INVALID,
		EXCEPTION,
		VALID
	}

	private static final class RecordingFailureReporter
			implements ClientRegionalTimeDilationPolicies
					.ProviderFailureReporter {
		private int invalidFactors;
		private int exceptions;

		@Override
		public void invalidFactor(
				ResourceLocation owner,
				float factor) {
			invalidFactors++;
		}

		@Override
		public void exception(
				ResourceLocation owner,
				RuntimeException error) {
			exceptions++;
		}
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
				"duplicate regional time-dilation owner was accepted");
	}

	private static void expectIllegalArgument(Runnable action) {
		try {
			action.run();
		}
		catch (IllegalArgumentException expected) {
			return;
		}
		throw new AssertionError(
				"invalid fractional tick factor was accepted");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
