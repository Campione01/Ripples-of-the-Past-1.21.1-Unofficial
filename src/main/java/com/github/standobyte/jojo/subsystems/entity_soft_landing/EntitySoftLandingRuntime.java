package com.github.standobyte.jojo.subsystems.entity_soft_landing;

import com.github.standobyte.jojo.api.block.EntitySoftLandingDecision;
import com.github.standobyte.jojo.api.block.EntitySoftLandingProviders;
import com.github.standobyte.jojo.api.block.EntitySoftLandingQuery;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class EntitySoftLandingRuntime {
	private EntitySoftLandingRuntime() {}

	public static boolean applyPostLandingMovement(
			BlockGetter blockGetter,
			BlockPos position,
			BlockState state,
			Entity entity) {
		if (!(blockGetter instanceof ServerLevel serverLevel)
				|| entity.level() != serverLevel
				|| entity.isSuppressingBounce()) {
			return false;
		}
		EntitySoftLandingDecision decision =
				EntitySoftLandingProviders.resolve(
						new EntitySoftLandingQuery(
								serverLevel,
								position,
								state,
								entity));
		Vec3 movement = entity.getDeltaMovement();
		if (!shouldApplyPostLandingMovement(decision, movement)) {
			return false;
		}
		entity.setDeltaMovement(
				movement.x,
				-movement.y
						* decision.verticalBounceMultiplier(),
				movement.z);
		return true;
	}

	static boolean shouldApplyPostLandingMovement(
			EntitySoftLandingDecision decision,
			Vec3 movement) {
		return decision.isHandled() && movement.y < 0.0D;
	}
}
