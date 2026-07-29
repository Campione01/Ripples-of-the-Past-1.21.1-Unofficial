package com.github.standobyte.jojo.api.gravity;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

public final class DirectionalGravityDataSmokeTest {
	private DirectionalGravityDataSmokeTest() {}

	public static void main(String[] args) {
		run();
		System.out.println(
				"Directional gravity data focused smoke passed.");
	}

	public static void run() {
		DirectionalGravityData data = new DirectionalGravityData();
		ResourceLocation firstId = id("a_source");
		ResourceLocation secondId = id("z_source");
		ResourceLocation highId = id("high_source");
		ResourceLocation nullId = id("null_source");

		MutableSource first = new MutableSource(Direction.WEST);
		MutableSource second = new MutableSource(Direction.EAST);
		MutableSource high = new MutableSource(Direction.NORTH);
		MutableSource nullSource = new MutableSource(null);

		check(data.resolve(null) == Direction.DOWN,
				"empty binding set must resolve DOWN");
		check(data.appliedDirection() == Direction.DOWN,
				"empty binding set must start with DOWN applied");

		data.bind(secondId, 5, second);
		data.bind(firstId, 5, first);
		check(data.resolve(null) == Direction.WEST,
				"equal priority must use the lower source ID");

		data.bind(highId, 10, high);
		data.bind(nullId, 20, nullSource);
		check(data.resolve(null) == Direction.NORTH,
				"higher active priority must win and null must be DOWN");
		check(!data.unbind(highId, first),
				"unbind must match source identity");
		check(data.resolve(null) == Direction.NORTH,
				"identity-mismatched unbind changed the winner");
		check(data.unbind(highId, high),
				"matching source must unbind");
		check(data.resolve(null) == Direction.WEST,
				"equal-priority order did not resume after unbind");

		MutableSource replacement = new MutableSource(Direction.SOUTH);
		data.bind(firstId, 5, replacement);
		check(!data.unbind(firstId, first),
				"replaced source must not unbind its successor");
		check(data.resolve(null) == Direction.SOUTH,
				"same-ID replacement did not become authoritative");

		check(data.updateAppliedDirection(data.resolve(null)),
				"first applied non-DOWN direction must report a change");
		check(data.appliedDirection() == Direction.SOUTH,
				"applied direction did not update");
		replacement.direction = Direction.UP;
		check(data.resolve(null) == Direction.UP,
				"mutable source direction did not resolve");
		check(data.updateAppliedDirection(data.resolve(null)),
				"directionChanged state must report a frame change");
		check(!data.updateAppliedDirection(data.resolve(null)),
				"unchanged direction must retain the exact fast path");
		check(data.updateAppliedDirection(Direction.DOWN),
				"explicit applied-frame change must report a change");
		check(data.appliedDirection() == Direction.DOWN,
				"explicit applied frame must be DOWN");
		check(data.updateAppliedDirection(data.resolve(null)),
				"resolved source must restore after an applied override");
		check(data.appliedDirection() == Direction.UP,
				"source frame did not resume after override");

		check(data.unbind(firstId, replacement),
				"replacement source did not unbind");
		check(data.resolve(null) == Direction.EAST,
				"remaining source did not become authoritative");
		check(data.unbind(secondId, second),
				"second source did not unbind");
		check(data.resolve(null) == Direction.DOWN,
				"all sources removed must resolve DOWN");

		failedBindingDoesNotCommit();
		runtimeFailureIsQuarantined();
		validBindingCommitsPastRuntimeFailure();
		unbindResolvesPastFailingSources();
	}

	private static void failedBindingDoesNotCommit() {
		DirectionalGravityData data = new DirectionalGravityData();
		ResourceLocation stableId = id("stable_source");
		ResourceLocation failedId = id("failed_source");
		MutableSource stable = new MutableSource(Direction.WEST);
		RuntimeException failure =
				new IllegalStateException("source failed");
		DirectionalGravitySource throwing = entity -> {
			throw failure;
		};

		data.bind(stableId, 5, stable);
		expectSameFailure(
				() -> data.bindAndResolve(
						null, failedId, 10, throwing),
				failure);
		check(!data.contains(failedId, throwing),
				"failed initial source remained bound");
		check(data.resolve(null) == Direction.WEST,
				"failed initial source changed the winner");

		expectSameFailure(
				() -> data.bindAndResolve(
						null, stableId, 10, throwing),
				failure);
		check(data.contains(stableId, stable),
				"failed replacement discarded the previous source");
		check(data.resolve(null) == Direction.WEST,
				"failed replacement changed the winner");
	}

	private static void runtimeFailureIsQuarantined() {
		DirectionalGravityData data = new DirectionalGravityData();
		ResourceLocation stableId = id("runtime_stable");
		ResourceLocation flakyId = id("runtime_flaky");
		MutableSource stable = new MutableSource(Direction.WEST);
		FlakySource flaky = new FlakySource(Direction.NORTH);

		data.bind(stableId, 5, stable);
		data.bind(flakyId, 10, flaky);
		check(data.updateAppliedDirection(data.resolve(null)),
				"runtime fixture did not apply its high-priority source");
		check(data.appliedDirection() == Direction.NORTH,
				"runtime fixture applied the wrong source");

		flaky.failure =
				new IllegalStateException("runtime source failed");
		check(data.resolve(null) == Direction.WEST,
				"runtime source failure escaped fail-safe resolution");
		check(data.updateAppliedDirection(data.resolve(null)),
				"runtime source failure did not change the applied frame");
		check(data.appliedDirection() == Direction.WEST,
				"quarantined source remained applied");
		int callsAfterFailure = flaky.calls;
		check(data.resolve(null) == Direction.WEST
						&& flaky.calls == callsAfterFailure,
				"quarantined source was invoked again without a retry");

		check(data.reactivate(flakyId, flaky),
				"matching source could not request a quarantine retry");
		check(data.resolve(null) == Direction.WEST
						&& flaky.calls == callsAfterFailure + 1,
				"a failed explicit retry did not re-quarantine");
		check(data.resolve(null) == Direction.WEST
						&& flaky.calls == callsAfterFailure + 1,
				"failed retry resumed repeated source calls");

		flaky.failure = null;
		check(data.reactivate(flakyId, flaky),
				"recovered source could not request a retry");
		check(data.resolve(null) == Direction.NORTH,
				"recovered source did not leave quarantine");
		check(data.updateAppliedDirection(data.resolve(null)),
				"recovered source did not restore the applied frame");
		check(data.appliedDirection() == Direction.NORTH,
				"recovered source applied the wrong frame");
	}

	private static void validBindingCommitsPastRuntimeFailure() {
		DirectionalGravityData data = new DirectionalGravityData();
		ResourceLocation flakyId = id("bind_existing_flaky");
		ResourceLocation replacementId = id("bind_valid");
		FlakySource flaky = new FlakySource(Direction.NORTH);
		MutableSource replacement =
				new MutableSource(Direction.WEST);

		data.bind(flakyId, 10, flaky);
		flaky.failure =
				new IllegalStateException("existing source failed");
		Direction resolved = data.bindAndResolve(
				null, replacementId, 5, replacement);
		check(resolved == Direction.WEST,
				"unrelated runtime failure rejected a valid binding");
		check(data.contains(replacementId, replacement),
				"valid binding was rolled back after another source failed");
		int callsAfterFailure = flaky.calls;
		check(data.resolve(null) == Direction.WEST
						&& flaky.calls == callsAfterFailure,
				"failed existing source was not quarantined during bind");
	}

	private static void unbindResolvesPastFailingSources() {
		DirectionalGravityData data = new DirectionalGravityData();
		ResourceLocation winnerId = id("unbind_winner");
		ResourceLocation flakyId = id("unbind_flaky");
		MutableSource winner = new MutableSource(Direction.UP);
		FlakySource flaky = new FlakySource(Direction.EAST);

		data.bind(winnerId, 10, winner);
		data.bind(flakyId, 5, flaky);
		check(data.updateAppliedDirection(data.resolve(null)),
				"unbind fixture did not apply its winner");
		flaky.failure =
				new IllegalStateException("remaining source failed");

		check(data.unbind(winnerId, winner),
				"winning source did not unbind");
		Direction resolved = data.resolve(null);
		check(resolved == Direction.DOWN,
				"unbind did not fail closed past a failing source");
		check(data.updateAppliedDirection(resolved)
						&& data.appliedDirection() == Direction.DOWN,
				"unbind left a stale applied direction");
		check(!data.contains(winnerId, winner),
				"unbind restored a successfully removed source");
	}

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath("rotp_test", path);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static void expectSameFailure(
			Runnable action, RuntimeException expected) {
		try {
			action.run();
			throw new AssertionError("expected source failure");
		}
		catch (RuntimeException actual) {
			check(actual == expected,
					"source failure identity was not preserved");
		}
	}

	private static final class MutableSource
			implements DirectionalGravitySource {
		private Direction direction;

		private MutableSource(Direction direction) {
			this.direction = direction;
		}

		@Override
		public Direction gravityDirection(
				net.minecraft.world.entity.Entity entity) {
			return direction;
		}
	}

	private static final class FlakySource
			implements DirectionalGravitySource {
		private Direction direction;
		private RuntimeException failure;
		private int calls;

		private FlakySource(Direction direction) {
			this.direction = direction;
		}

		@Override
		public Direction gravityDirection(
				net.minecraft.world.entity.Entity entity) {
			calls++;
			if (failure != null) {
				throw failure;
			}
			return direction;
		}
	}
}
