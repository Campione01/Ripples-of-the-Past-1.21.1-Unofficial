package com.github.standobyte.jojo.subsystems.directional_gravity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.github.standobyte.jojo.api.gravity.DirectionalGravityTransforms;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Axis-permuted vanilla collision and step-up calculation.
 */
public final class DirectionalGravityCollision {
	private static final double STEP_EPSILON = 1.0E-5;

	private DirectionalGravityCollision() {}

	public static Vec3 collide(Entity entity, Vec3 requested,
			Direction gravity) {
		AABB box = entity.getBoundingBox();
		Vec3 localRequested =
				DirectionalGravityTransforms.toLocal(gravity, requested);
		float maxStep = entity.maxUpStep();
		Vec3 stepEnvelope = DirectionalGravityTransforms.toWorld(
				gravity,
				new Vec3(localRequested.x, maxStep, localRequested.z));
		AABB envelope = box.expandTowards(requested)
				.expandTowards(stepEnvelope);
		List<VoxelShape> colliders =
				collectColliders(entity, envelope);

		Vec3 actual = collideWithShapes(
				gravity, requested, box, colliders);
		Vec3 localActual =
				DirectionalGravityTransforms.toLocal(gravity, actual);
		boolean gravityCollision =
				!same(localRequested.y, localActual.y);
		boolean groundCollision =
				gravityCollision && localRequested.y < 0.0;
		boolean transverseCollision =
				!same(localRequested.x, localActual.x)
				|| !same(localRequested.z, localActual.z);
		if (maxStep <= 0.0F
				|| !(groundCollision || entity.onGround())
				|| !transverseCollision) {
			return actual;
		}

		AABB stepBase = groundCollision
				? box.move(DirectionalGravityTransforms.toWorld(
						gravity,
						new Vec3(0.0, localActual.y, 0.0)))
				: box;
		List<Double> candidates = candidateStepHeights(
				stepBase, colliders, gravity, maxStep,
				localActual.y);
		double actualTransverse =
				localActual.x * localActual.x
				+ localActual.z * localActual.z;
		for (double height : candidates) {
			Vec3 candidateRequest =
					DirectionalGravityTransforms.toWorld(
							gravity,
							new Vec3(localRequested.x, height,
									localRequested.z));
			Vec3 candidate = collideWithShapes(
					gravity, candidateRequest, stepBase, colliders);
			Vec3 localCandidate =
					DirectionalGravityTransforms.toLocal(
							gravity, candidate);
			double candidateTransverse =
					localCandidate.x * localCandidate.x
					+ localCandidate.z * localCandidate.z;
			if (candidateTransverse > actualTransverse) {
				return DirectionalGravityTransforms.toWorld(
						gravity,
						localCandidate.add(0.0,
								groundCollision
										? localActual.y : 0.0,
								0.0));
			}
		}
		return actual;
	}

	static List<Double> candidateStepHeights(AABB box,
			List<VoxelShape> colliders, Direction gravity,
			double maxStep, double excludedHeight) {
		Direction localUp = gravity.getOpposite();
		Direction.Axis axis = localUp.getAxis();
		boolean positive =
				localUp.getAxisDirection()
						== Direction.AxisDirection.POSITIVE;
		double foot = positive
				? min(box, axis) : max(box, axis);
		List<Double> candidates = new ArrayList<>();
		for (VoxelShape shape : colliders) {
			for (double coordinate : shape.getCoords(axis)) {
				double height = positive
						? coordinate - foot : foot - coordinate;
				if (height >= 0.0 && height <= maxStep
						&& !same(height, excludedHeight)
						&& candidates.stream().noneMatch(
								value -> same(value, height))) {
					candidates.add(height);
				}
			}
		}
		candidates.sort(Comparator.naturalOrder());
		return candidates;
	}

	private static List<VoxelShape> collectColliders(
			Entity entity, AABB envelope) {
		Level level = entity.level();
		List<VoxelShape> colliders = new ArrayList<>(
				level.getEntityCollisions(entity, envelope));
		for (VoxelShape shape
				: level.getBlockCollisions(entity, envelope)) {
			colliders.add(shape);
		}
		WorldBorder border = level.getWorldBorder();
		if (border.isInsideCloseToBorder(entity, envelope)) {
			colliders.add(border.getCollisionShape());
		}
		return colliders;
	}

	private static Vec3 collideWithShapes(Direction gravity,
			Vec3 requested, AABB box, List<VoxelShape> shapes) {
		if (shapes.isEmpty()) {
			return requested;
		}
		Vec3 local =
				DirectionalGravityTransforms.toLocal(gravity, requested);
		Direction.Axis localVertical =
				worldAxis(gravity, Direction.Axis.Y);
		Direction.Axis localX =
				worldAxis(gravity, Direction.Axis.X);
		Direction.Axis localZ =
				worldAxis(gravity, Direction.Axis.Z);
		double[] movement = {
				requested.x, requested.y, requested.z
		};
		box = collideAxis(localVertical, box, shapes, movement);
		boolean zFirst = Math.abs(local.x) < Math.abs(local.z);
		if (zFirst) {
			box = collideAxis(localZ, box, shapes, movement);
		}
		box = collideAxis(localX, box, shapes, movement);
		if (!zFirst) {
			collideAxis(localZ, box, shapes, movement);
		}
		return new Vec3(movement[0], movement[1], movement[2]);
	}

	private static AABB collideAxis(Direction.Axis axis, AABB box,
			List<VoxelShape> shapes, double[] movement) {
		int index = axis.ordinal();
		double amount = movement[index];
		if (amount != 0.0) {
			amount = Shapes.collide(axis, box, shapes, amount);
			movement[index] = amount;
			if (amount != 0.0) {
				box = box.move(axis == Direction.Axis.X ? amount : 0.0,
						axis == Direction.Axis.Y ? amount : 0.0,
						axis == Direction.Axis.Z ? amount : 0.0);
			}
		}
		return box;
	}

	private static Direction.Axis worldAxis(Direction gravity,
			Direction.Axis localAxis) {
		Vec3 basis = switch (localAxis) {
		case X -> new Vec3(1.0, 0.0, 0.0);
		case Y -> new Vec3(0.0, 1.0, 0.0);
		case Z -> new Vec3(0.0, 0.0, 1.0);
		};
		Vec3 world =
				DirectionalGravityTransforms.toWorld(gravity, basis);
		if (world.x != 0.0) {
			return Direction.Axis.X;
		}
		if (world.y != 0.0) {
			return Direction.Axis.Y;
		}
		return Direction.Axis.Z;
	}

	private static double min(AABB box, Direction.Axis axis) {
		return switch (axis) {
		case X -> box.minX;
		case Y -> box.minY;
		case Z -> box.minZ;
		};
	}

	private static double max(AABB box, Direction.Axis axis) {
		return switch (axis) {
		case X -> box.maxX;
		case Y -> box.maxY;
		case Z -> box.maxZ;
		};
	}

	private static boolean same(double first, double second) {
		return Math.abs(first - second) < 1.0E-7;
	}
}
