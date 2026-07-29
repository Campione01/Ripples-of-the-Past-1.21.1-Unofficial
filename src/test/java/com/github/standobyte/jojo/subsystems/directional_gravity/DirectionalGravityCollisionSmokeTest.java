package com.github.standobyte.jojo.subsystems.directional_gravity;

import java.util.List;

import com.github.standobyte.jojo.api.gravity.DirectionalGravityTransforms;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class DirectionalGravityCollisionSmokeTest {
	private DirectionalGravityCollisionSmokeTest() {}

	public static void run() {
		AABB localEntity =
				new AABB(0.0, 0.0, 0.0, 0.6, 1.8, 0.6);
		AABB localStep =
				new AABB(0.4, 0.0, 0.0, 1.0, 0.5, 0.6);
		for (Direction direction : Direction.values()) {
			AABB entity = DirectionalGravityTransforms.rotateBox(
					direction, localEntity, Vec3.ZERO);
			AABB step = DirectionalGravityTransforms.rotateBox(
					direction, localStep, Vec3.ZERO);
			VoxelShape shape = Shapes.create(step);
			List<Double> candidates =
					DirectionalGravityCollision
							.candidateStepHeights(
									entity, List.of(shape),
									direction, 0.6, -0.1);
			check(candidates.stream().anyMatch(
					height -> Math.abs(height - 0.5)
							< 1.0E-7),
					direction
							+ " step surface must produce local height");

			Vec3 localRise = new Vec3(0.2, 0.5, 0.0);
			Vec3 worldRise =
					DirectionalGravityTransforms.toWorld(
							direction, localRise);
			check(Math.abs(DirectionalGravityTransforms.toLocal(
					direction, worldRise).y - 0.5)
					< 1.0E-9,
					direction
							+ " accepted step must remain local-up");
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
