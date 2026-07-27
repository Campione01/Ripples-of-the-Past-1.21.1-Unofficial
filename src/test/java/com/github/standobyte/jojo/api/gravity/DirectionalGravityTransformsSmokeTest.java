package com.github.standobyte.jojo.api.gravity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class DirectionalGravityTransformsSmokeTest {
	private static final double EPSILON = 1.0E-9;

	private DirectionalGravityTransformsSmokeTest() {}

	public static void run() {
		Vec3 sample = new Vec3(0.25, -1.5, 2.75);
		check(DirectionalGravityTransforms.toWorld(
				Direction.DOWN, sample) == sample,
				"DOWN must preserve the exact vector fast path");

		for (Direction direction : Direction.values()) {
			verifyRoundTrip(direction, sample);
			verifyGravityBasis(direction);
			verifyCollisionBox(direction);
			verifyGroundCollision(direction);
			verifyEyePosition(direction);
			verifyFloor(direction);
			verifyMovement(direction);
			verifyCameraFrame(direction);
			verifyGravityAndFriction(direction);
		}
	}

	private static void verifyGroundCollision(Direction direction) {
		Vec3 withGravity = Vec3.atLowerCornerOf(
				direction.getNormal()).scale(0.2);
		DirectionalGravityTransforms.RelativeCollision landing =
				DirectionalGravityTransforms.relativeCollision(
						direction, withGravity, Vec3.ZERO);
		check(landing.gravityAxisCollision()
				&& landing.groundCollision()
				&& !landing.transverseCollision(),
				direction + " collision must classify as ground");
		checkClose(DirectionalGravityTransforms.toLocal(
				direction, withGravity).y, -0.2,
				direction
						+ " falling movement must accumulate fall distance");

		DirectionalGravityTransforms.RelativeCollision ceiling =
				DirectionalGravityTransforms.relativeCollision(
						direction, withGravity.scale(-1.0), Vec3.ZERO);
		check(ceiling.gravityAxisCollision()
				&& !ceiling.groundCollision(),
				direction + " opposite collision must not be ground");
		checkClose(DirectionalGravityTransforms.toLocal(
				direction, withGravity.scale(-1.0)).y, 0.2,
				direction
						+ " movement away from gravity must not accumulate");

		Vec3 transverse = DirectionalGravityTransforms.toWorld(
				direction, new Vec3(0.2, 0.0, 0.0));
		DirectionalGravityTransforms.RelativeCollision wall =
				DirectionalGravityTransforms.relativeCollision(
						direction, transverse, Vec3.ZERO);
		check(!wall.gravityAxisCollision()
				&& wall.transverseCollision(),
				direction + " transverse collision classification");
		checkClose(
				DirectionalGravityTransforms.stopCollidedVelocity(
						new Vec3(1.0, 2.0, 3.0), landing),
				stopAxis(new Vec3(1.0, 2.0, 3.0),
						direction.getAxis()),
				direction + " landing must stop gravity-axis velocity");

		AABB box = new AABB(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3);
		AABB support = DirectionalGravityTransforms.supportingBox(
				direction, box, 1.0E-6);
		double supportThickness = switch (direction.getAxis()) {
		case X -> support.getXsize();
		case Y -> support.getYsize();
		case Z -> support.getZsize();
		};
		checkClose(supportThickness, 1.0E-6,
				direction + " support slab thickness");

		Vec3 localJump = new Vec3(0.0, 0.42, 0.0);
		Vec3 worldJump = DirectionalGravityTransforms.toWorld(
				direction, localJump);
		double awayFromGravity = worldJump.dot(
				Vec3.atLowerCornerOf(direction.getNormal()));
		checkClose(awayFromGravity, -0.42,
				direction + " jump must point away from gravity");
		check(DirectionalGravityTransforms.isMovingWithGravity(
				direction, withGravity),
				direction + " gravity-dot falling classification");
		check(!DirectionalGravityTransforms.isMovingWithGravity(
				direction, withGravity.scale(-1.0)),
				direction + " gravity-dot rising classification");
	}

	private static void verifyRoundTrip(Direction direction, Vec3 sample) {
		Vec3 world = DirectionalGravityTransforms.toWorld(
				direction, sample);
		Vec3 roundTrip = DirectionalGravityTransforms.toLocal(
				direction, world);
		checkClose(roundTrip, sample,
				direction + " transform must round-trip");
	}

	private static void verifyGravityBasis(Direction direction) {
		Vec3 localDown = new Vec3(0.0, -1.0, 0.0);
		Vec3 expected = Vec3.atLowerCornerOf(direction.getNormal());
		checkClose(DirectionalGravityTransforms.toWorld(
				direction, localDown), expected,
				direction + " local down must map to gravity");

		Vec3 x = DirectionalGravityTransforms.toWorld(
				direction, new Vec3(1.0, 0.0, 0.0));
		Vec3 y = DirectionalGravityTransforms.toWorld(
				direction, new Vec3(0.0, 1.0, 0.0));
		Vec3 z = DirectionalGravityTransforms.toWorld(
				direction, new Vec3(0.0, 0.0, 1.0));
		checkClose(x.length(), 1.0, "basis x length");
		checkClose(y.length(), 1.0, "basis y length");
		checkClose(z.length(), 1.0, "basis z length");
		checkClose(x.dot(y), 0.0, "basis x/y orthogonality");
		checkClose(x.dot(z), 0.0, "basis x/z orthogonality");
		checkClose(y.dot(z), 0.0, "basis y/z orthogonality");
	}

	private static void verifyCollisionBox(Direction direction) {
		Vec3 origin = new Vec3(10.0, 64.0, -4.0);
		AABB local = new AABB(
				9.7, 64.0, -4.3, 10.3, 65.8, -3.7);
		AABB rotated = DirectionalGravityTransforms.rotateBox(
				direction, local, origin);
		double gravityAxisSize = switch (direction.getAxis()) {
		case X -> rotated.getXsize();
		case Y -> rotated.getYsize();
		case Z -> rotated.getZsize();
		};
		checkClose(gravityAxisSize, 1.8,
				direction + " body height must follow gravity axis");

		Vec3 localFoot = origin;
		Vec3 localHead = origin.add(0.0, 1.8, 0.0);
		Vec3 expectedHead = origin.add(
				DirectionalGravityTransforms.toWorld(direction,
						new Vec3(0.0, 1.8, 0.0)));
		check(rotated.contains(localFoot)
				|| onBoundary(rotated, localFoot),
				direction + " rotated box must retain foot origin");
		check(rotated.contains(expectedHead)
				|| onBoundary(rotated, expectedHead),
				direction + " rotated box must contain transformed head");
		checkClose(localHead.distanceTo(origin), 1.8,
				"local fixture height drifted");
	}

	private static void verifyEyePosition(Direction direction) {
		Vec3 base = new Vec3(3.5, 70.25, -8.5);
		Vec3 eye = DirectionalGravityTransforms.eyePosition(
				direction, base, 1.62);
		Vec3 expected = base.add(
				DirectionalGravityTransforms.toWorld(direction,
						new Vec3(0.0, 1.62, 0.0)));
		checkClose(eye, expected,
				direction + " eye offset must use gravity frame");
		checkClose(eye.distanceTo(base), 1.62,
				direction + " eye height must be preserved");
	}

	private static void verifyFloor(Direction direction) {
		Vec3 base = new Vec3(10.5, 64.5, -2.5);
		BlockPos expected =
				BlockPos.containing(base).relative(direction);
		BlockPos actual =
				DirectionalGravityTransforms.floorBlockPos(
						direction, base, 0.500001);
		check(expected.equals(actual),
				direction + " floor lookup must follow gravity");
	}

	private static void verifyMovement(Direction direction) {
		Vec3 localInput = new Vec3(0.35, 0.2, 0.8);
		Vec3 worldInput = DirectionalGravityTransforms.toWorld(
				direction, localInput);
		checkClose(DirectionalGravityTransforms.toLocal(
				direction, worldInput), localInput,
				direction + " movement input must round-trip");

		Vec3 movement = new Vec3(2.0, 3.0, 5.0);
		Vec3 scaled =
				DirectionalGravityTransforms.applyBlockSpeedFactor(
						direction, movement, 0.4);
		double preserved = component(scaled, direction.getAxis());
		double original = component(movement, direction.getAxis());
		checkClose(preserved, original,
				direction + " block speed must preserve gravity axis");
		for (Direction.Axis axis : Direction.Axis.values()) {
			if (axis != direction.getAxis()) {
				checkClose(component(scaled, axis),
						component(movement, axis) * 0.4,
						direction + " block speed transverse axis");
			}
		}
	}

	private static void verifyCameraFrame(Direction direction) {
		Vec3 localForward = new Vec3(0.0, 0.0, -1.0);
		Vec3 localUp = new Vec3(0.0, 1.0, 0.0);
		Vec3 worldForward = DirectionalGravityTransforms.toWorld(
				direction, localForward);
		Vec3 worldUp = DirectionalGravityTransforms.toWorld(
				direction, localUp);
		checkClose(worldForward.length(), 1.0,
				direction + " camera forward length");
		checkClose(worldUp.length(), 1.0,
				direction + " camera up length");
		checkClose(worldForward.dot(worldUp), 0.0,
				direction + " camera forward/up orthogonality");
		checkClose(worldUp.dot(Vec3.atLowerCornerOf(
				direction.getNormal())), -1.0,
				direction + " camera up must oppose gravity");
	}

	private static void verifyGravityAndFriction(Direction direction) {
		double gravity = 0.08;
		Vec3 local = new Vec3(1.0, 2.0, 3.0);
		Vec3 movement =
				DirectionalGravityTransforms.toWorld(direction, local);
		Vec3 adjusted =
				DirectionalGravityTransforms.applyGravityAndFriction(
						direction, movement, gravity, 0.91, 0.98);
		Vec3 expected = DirectionalGravityTransforms.toWorld(
				direction,
				new Vec3(0.91, (2.0 - gravity) * 0.98, 2.73));
		checkClose(adjusted, expected,
				direction + " gravity-frame friction must be exact");

		Vec3 frictionless =
				DirectionalGravityTransforms.applyGravityAndFriction(
						direction, movement, gravity, 1.0, 1.0);
		checkClose(frictionless,
				DirectionalGravityTransforms.toWorld(
						direction,
						new Vec3(1.0, 2.0 - gravity, 3.0)),
				direction + " discarded friction must preserve axes");
	}

	private static double component(Vec3 vector, Direction.Axis axis) {
		return switch (axis) {
		case X -> vector.x;
		case Y -> vector.y;
		case Z -> vector.z;
		};
	}

	private static Vec3 stopAxis(Vec3 vector, Direction.Axis axis) {
		return switch (axis) {
		case X -> new Vec3(0.0, vector.y, vector.z);
		case Y -> new Vec3(vector.x, 0.0, vector.z);
		case Z -> new Vec3(vector.x, vector.y, 0.0);
		};
	}

	private static boolean onBoundary(AABB box, Vec3 point) {
		return point.x >= box.minX - EPSILON
				&& point.x <= box.maxX + EPSILON
				&& point.y >= box.minY - EPSILON
				&& point.y <= box.maxY + EPSILON
				&& point.z >= box.minZ - EPSILON
				&& point.z <= box.maxZ + EPSILON;
	}

	private static void checkClose(Vec3 actual, Vec3 expected,
			String message) {
		checkClose(actual.x, expected.x, message + " x");
		checkClose(actual.y, expected.y, message + " y");
		checkClose(actual.z, expected.z, message + " z");
	}

	private static void checkClose(double actual, double expected,
			String message) {
		if (Math.abs(actual - expected) > EPSILON) {
			throw new AssertionError(message + ": expected "
					+ expected + ", got " + actual);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
