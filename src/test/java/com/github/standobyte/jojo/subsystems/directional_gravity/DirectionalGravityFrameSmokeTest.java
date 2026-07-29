package com.github.standobyte.jojo.subsystems.directional_gravity;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public final class DirectionalGravityFrameSmokeTest {
	private static final double EPSILON = 1.0E-9;

	private DirectionalGravityFrameSmokeTest() {}

	public static void run() {
		for (Direction direction
				: new Direction[] {Direction.NORTH, Direction.EAST}) {
			Vec3 world = new Vec3(0.25, -0.5, 0.75);
			Vec3 outerLocal =
					DirectionalGravityRuntime.enterFrameVelocity(
							direction, world, 0);
			Vec3 nestedLocal =
					DirectionalGravityRuntime.enterFrameVelocity(
							direction, outerLocal, 1);
			checkClose(nestedLocal, outerLocal,
					direction
							+ " nested enter must not transform twice");

			Vec3 afterNestedExit =
					DirectionalGravityRuntime.exitFrameVelocity(
							direction, nestedLocal, 1);
			checkClose(afterNestedExit, outerLocal,
					direction
							+ " nested exit must not restore early");
			Vec3 afterOuterExit =
					DirectionalGravityRuntime.exitFrameVelocity(
							direction, afterNestedExit, 0);
			checkClose(afterOuterExit, world,
					direction
							+ " outer exit must restore world once");
		}
	}

	private static void checkClose(Vec3 actual, Vec3 expected,
			String message) {
		if (actual.distanceToSqr(expected) > EPSILON * EPSILON) {
			throw new AssertionError(message + ": expected "
					+ expected + ", got " + actual);
		}
	}
}
