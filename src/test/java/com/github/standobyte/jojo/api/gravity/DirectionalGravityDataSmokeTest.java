package com.github.standobyte.jojo.api.gravity;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

public final class DirectionalGravityDataSmokeTest {
	private DirectionalGravityDataSmokeTest() {}

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
				"temporary compatibility suppression must change frame");
		check(data.appliedDirection() == Direction.DOWN,
				"suppressed frame must be DOWN");
		check(data.updateAppliedDirection(data.resolve(null)),
				"leaving compatibility suppression must restore source");
		check(data.appliedDirection() == Direction.UP,
				"source frame did not resume after suppression");

		check(data.unbind(firstId, replacement),
				"replacement source did not unbind");
		check(data.resolve(null) == Direction.EAST,
				"remaining source did not become authoritative");
		check(data.unbind(secondId, second),
				"second source did not unbind");
		check(data.resolve(null) == Direction.DOWN,
				"all sources removed must resolve DOWN");
	}

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath("rotp_test", path);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
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
}
