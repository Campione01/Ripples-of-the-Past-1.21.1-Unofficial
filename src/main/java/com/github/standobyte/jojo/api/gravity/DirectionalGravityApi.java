package com.github.standobyte.jojo.api.gravity;

import java.util.Objects;

import com.github.standobyte.jojo.init.ModDataAttachmentTypes;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Runtime binding surface for {@code directional_gravity_v1}.
 *
 * <p>The core stores only loaded-entity bindings. A source remains responsible
 * for authoritative state, persistence, and server-to-client synchronization.
 * Higher priorities win among active sources whose direction is not
 * {@link Direction#DOWN}; equal-priority sources are ordered by source ID.
 * Version 1 supports living entities only.</p>
 */
public final class DirectionalGravityApi {
	public static final int DEFAULT_PRIORITY = 0;

	private DirectionalGravityApi() {}

	public static void bind(LivingEntity entity, ResourceLocation sourceId,
			DirectionalGravitySource source) {
		bind(entity, sourceId, DEFAULT_PRIORITY, source);
	}

	public static void bind(LivingEntity entity, ResourceLocation sourceId,
			int priority, DirectionalGravitySource source) {
		Objects.requireNonNull(entity, "entity");
		Objects.requireNonNull(sourceId, "sourceId");
		Objects.requireNonNull(source, "source");

		DirectionalGravityData data = entity.getData(
				ModDataAttachmentTypes.DIRECTIONAL_GRAVITY.get());
		data.bind(sourceId, priority, source);
		if (data.updateAppliedDirection(
				effectiveDirection(entity, data.resolve(entity)))) {
			entity.refreshDimensions();
		}
	}

	public static void unbind(LivingEntity entity, ResourceLocation sourceId,
			DirectionalGravitySource source) {
		if (entity == null || sourceId == null || source == null) {
			return;
		}
		DirectionalGravityData data = existingData(entity);
		if (data == null) {
			return;
		}
		if (data.unbind(sourceId, source)
				&& data.updateAppliedDirection(effectiveDirection(
						entity, data.resolve(entity)))) {
			entity.refreshDimensions();
		}
	}

	/**
	 * Refreshes collision dimensions after a bound source changes its state.
	 */
	public static void directionChanged(LivingEntity entity,
			ResourceLocation sourceId, DirectionalGravitySource source) {
		if (entity == null || sourceId == null || source == null) {
			return;
		}
		DirectionalGravityData data = existingData(entity);
		if (data != null && data.contains(sourceId, source)
				&& data.updateAppliedDirection(effectiveDirection(
						entity, data.resolve(entity)))) {
			entity.refreshDimensions();
		}
	}

	public static Direction getDirection(Entity entity) {
		if (entity == null) {
			return Direction.DOWN;
		}
		DirectionalGravityData data = existingData(entity);
		return data != null ? data.resolve(entity) : Direction.DOWN;
	}

	/**
	 * Returns the direction used by core hooks. Vanilla behavior is retained
	 * for non-living entities, vehicles, fluids, elytra flight, levitation,
	 * no-gravity entities, and player ability flight because those movement
	 * models are not part of v1.
	 */
	public static Direction getEffectiveDirection(Entity entity) {
		if (entity == null) {
			return Direction.DOWN;
		}
		DirectionalGravityData data = existingData(entity);
		return data != null ? data.appliedDirection() : Direction.DOWN;
	}

	/**
	 * Reconciles temporary compatibility modes before an entity tick. This
	 * keeps cached collision geometry and all movement hooks in one frame.
	 */
	public static void reconcileEffectiveDirection(Entity entity) {
		DirectionalGravityData data = existingData(entity);
		if (data != null && data.updateAppliedDirection(
				effectiveDirection(entity, data.resolve(entity)))) {
			entity.refreshDimensions();
		}
	}

	private static Direction effectiveDirection(Entity entity,
			Direction direction) {
		if (!(entity instanceof LivingEntity living)
				|| direction == Direction.DOWN || entity.isPassenger()
				|| entity.isNoGravity()
				|| living.hasEffect(
						net.minecraft.world.effect.MobEffects.LEVITATION)) {
			return Direction.DOWN;
		}
		if (living.isSwimming() || living.isInWaterOrBubble()
				|| living.isInLava() || living.isInFluidType()
				|| living.isFallFlying()) {
			return Direction.DOWN;
		}
		if (entity instanceof Player player
				&& player.getAbilities().flying) {
			return Direction.DOWN;
		}
		return direction;
	}

	private static DirectionalGravityData existingData(Entity entity) {
		if (!(entity instanceof LivingEntity)) {
			return null;
		}
		return entity.getExistingDataOrNull(
				ModDataAttachmentTypes.DIRECTIONAL_GRAVITY.get());
	}
}
