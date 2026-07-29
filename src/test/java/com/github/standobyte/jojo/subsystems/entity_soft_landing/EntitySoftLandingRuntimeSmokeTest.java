package com.github.standobyte.jojo.subsystems.entity_soft_landing;

import com.github.standobyte.jojo.api.block.EntitySoftLandingDecision;
import com.github.standobyte.jojo.api.gravity.DirectionalGravityTransforms;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public final class EntitySoftLandingRuntimeSmokeTest {
	private static int assertions;

	private EntitySoftLandingRuntimeSmokeTest() {}

	public static void run() {
		assertions = 0;
		EntitySoftLandingDecision handled =
				EntitySoftLandingDecision.softBounce(0.0F, 0.8D);

		check(!EntitySoftLandingRuntime
						.shouldApplyPostLandingMovement(
								handled,
								new Vec3(0.1, 0.4, -0.2)),
				"DOWN upward collision swallowed vanilla response");
		check(!EntitySoftLandingRuntime
						.shouldApplyPostLandingMovement(
								handled,
								new Vec3(0.1, 0.0, -0.2)),
				"stationary contact swallowed vanilla response");
		check(EntitySoftLandingRuntime
						.shouldApplyPostLandingMovement(
								handled,
								new Vec3(0.1, -0.4, -0.2)),
				"DOWN downward landing did not apply soft bounce");
		check(!EntitySoftLandingRuntime
						.shouldApplyPostLandingMovement(
								EntitySoftLandingDecision.pass(),
								new Vec3(0.1, -0.4, -0.2)),
				"PASS decision swallowed vanilla response");

		Vec3 eastLocalImpact = new Vec3(0.1, -0.6, -0.2);
		Vec3 eastWorldImpact =
				DirectionalGravityTransforms.toWorld(
						Direction.EAST, eastLocalImpact);
		check(eastWorldImpact.y > 0.0D
						&& EntitySoftLandingRuntime
								.shouldApplyPostLandingMovement(
										handled,
										DirectionalGravityTransforms
												.toLocal(
														Direction.EAST,
														eastWorldImpact)),
				"EAST local downward landing used world Y");

		System.out.println(
				"Entity soft-landing runtime smoke passed with "
						+ assertions + " assertions.");
	}

	private static void check(boolean condition, String message) {
		assertions++;
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
