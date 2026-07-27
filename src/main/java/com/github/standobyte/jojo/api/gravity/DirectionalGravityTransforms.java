package com.github.standobyte.jojo.api.gravity;

import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Pure coordinate transforms used by {@code directional_gravity_v1}.
 */
public final class DirectionalGravityTransforms {
	private DirectionalGravityTransforms() {}

	public static Vec3 toWorld(Direction gravity, Vec3 local) {
		Objects.requireNonNull(gravity, "gravity");
		Objects.requireNonNull(local, "local");
		double x = local.x;
		double y = local.y;
		double z = local.z;
		return switch (gravity) {
		case DOWN -> local;
		case UP -> new Vec3(-x, -y, z);
		case NORTH -> new Vec3(x, -z, y);
		case SOUTH -> new Vec3(-x, -z, -y);
		case WEST -> new Vec3(y, -z, -x);
		case EAST -> new Vec3(-y, -z, x);
		};
	}

	public static Vec3 toLocal(Direction gravity, Vec3 world) {
		Objects.requireNonNull(gravity, "gravity");
		Objects.requireNonNull(world, "world");
		double x = world.x;
		double y = world.y;
		double z = world.z;
		return switch (gravity) {
		case DOWN -> world;
		case UP -> new Vec3(-x, -y, z);
		case NORTH -> new Vec3(x, z, -y);
		case SOUTH -> new Vec3(-x, -z, -y);
		case WEST -> new Vec3(-z, x, -y);
		case EAST -> new Vec3(z, -x, -y);
		};
	}

	public static AABB rotateBox(Direction gravity, AABB localBox,
			Vec3 origin) {
		Objects.requireNonNull(gravity, "gravity");
		Objects.requireNonNull(localBox, "localBox");
		Objects.requireNonNull(origin, "origin");
		if (gravity == Direction.DOWN) {
			return localBox;
		}

		double minX = Double.POSITIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double minZ = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;
		double maxZ = Double.NEGATIVE_INFINITY;
		for (double x : new double[] {localBox.minX, localBox.maxX}) {
			for (double y : new double[] {localBox.minY, localBox.maxY}) {
				for (double z : new double[] {localBox.minZ, localBox.maxZ}) {
					Vec3 corner = toWorld(gravity,
							new Vec3(x - origin.x, y - origin.y,
									z - origin.z)).add(origin);
					minX = Math.min(minX, corner.x);
					minY = Math.min(minY, corner.y);
					minZ = Math.min(minZ, corner.z);
					maxX = Math.max(maxX, corner.x);
					maxY = Math.max(maxY, corner.y);
					maxZ = Math.max(maxZ, corner.z);
				}
			}
		}
		return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
	}

	public static Vec3 eyePosition(Direction gravity, Vec3 basePosition,
			double eyeHeight) {
		if (gravity == Direction.DOWN) {
			return basePosition.add(0.0, eyeHeight, 0.0);
		}
		return basePosition.add(toWorld(
				gravity, new Vec3(0.0, eyeHeight, 0.0)));
	}

	public static BlockPos floorBlockPos(Direction gravity, Vec3 position,
			double offset) {
		Vec3 normal = Vec3.atLowerCornerOf(gravity.getNormal());
		return BlockPos.containing(position.add(normal.scale(offset)));
	}

	public static Vec3 applyBlockSpeedFactor(Direction gravity,
			Vec3 movement, double factor) {
		return switch (gravity.getAxis()) {
		case X -> movement.multiply(1.0, factor, factor);
		case Y -> movement.multiply(factor, 1.0, factor);
		case Z -> movement.multiply(factor, factor, 1.0);
		};
	}

	public static RelativeCollision relativeCollision(Direction gravity,
			Vec3 requested, Vec3 actual) {
		boolean x = !same(requested.x, actual.x);
		boolean y = !same(requested.y, actual.y);
		boolean z = !same(requested.z, actual.z);
		boolean gravityAxisCollision = switch (gravity.getAxis()) {
		case X -> x;
		case Y -> y;
		case Z -> z;
		};
		double requestedWithGravity = requested.dot(
				Vec3.atLowerCornerOf(gravity.getNormal()));
		boolean transverseCollision = switch (gravity.getAxis()) {
		case X -> y || z;
		case Y -> x || z;
		case Z -> x || y;
		};
		return new RelativeCollision(
				gravityAxisCollision,
				gravityAxisCollision && requestedWithGravity > 0.0,
				transverseCollision,
				x, y, z);
	}

	public static Vec3 stopCollidedVelocity(Vec3 velocity,
			RelativeCollision collision) {
		return new Vec3(
				collision.xCollision() ? 0.0 : velocity.x,
				collision.yCollision() ? 0.0 : velocity.y,
				collision.zCollision() ? 0.0 : velocity.z);
	}

	public static AABB supportingBox(Direction gravity, AABB box,
			double epsilon) {
		return switch (gravity) {
		case DOWN -> new AABB(box.minX, box.minY - epsilon, box.minZ,
				box.maxX, box.minY, box.maxZ);
		case UP -> new AABB(box.minX, box.maxY, box.minZ,
				box.maxX, box.maxY + epsilon, box.maxZ);
		case NORTH -> new AABB(box.minX, box.minY, box.minZ - epsilon,
				box.maxX, box.maxY, box.minZ);
		case SOUTH -> new AABB(box.minX, box.minY, box.maxZ,
				box.maxX, box.maxY, box.maxZ + epsilon);
		case WEST -> new AABB(box.minX - epsilon, box.minY, box.minZ,
				box.minX, box.maxY, box.maxZ);
		case EAST -> new AABB(box.maxX, box.minY, box.minZ,
				box.maxX + epsilon, box.maxY, box.maxZ);
		};
	}

	public static Vec3 transverseMovement(Direction gravity,
			Vec3 movement) {
		return switch (gravity.getAxis()) {
		case X -> new Vec3(0.0, movement.y, movement.z);
		case Y -> new Vec3(movement.x, 0.0, movement.z);
		case Z -> new Vec3(movement.x, movement.y, 0.0);
		};
	}

	public static boolean isMovingWithGravity(Direction gravity,
			Vec3 movement) {
		return movement.dot(Vec3.atLowerCornerOf(
				gravity.getNormal())) >= 0.0;
	}

	public static Vec3 applyGravityAndFriction(Direction gravity,
			Vec3 movement, double acceleration,
			double transverseFriction, double gravityFriction) {
		Vec3 local = toLocal(gravity, movement);
		Vec3 adjusted = new Vec3(
				local.x * transverseFriction,
				(local.y - acceleration) * gravityFriction,
				local.z * transverseFriction);
		return toWorld(gravity, adjusted);
	}

	private static boolean same(double first, double second) {
		return Math.abs(first - second) < 1.0E-7;
	}

	public record RelativeCollision(
			boolean gravityAxisCollision,
			boolean groundCollision,
			boolean transverseCollision,
			boolean xCollision,
			boolean yCollision,
			boolean zCollision) {}
}
