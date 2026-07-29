package com.github.standobyte.jojo.api.gravity;

import java.util.Objects;

import com.github.standobyte.jojo.init.ModDataAttachmentTypes;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Runtime binding surface for directional gravity v1 and v2.
 *
 * <p>The core stores only loaded-entity bindings. A source remains responsible
 * for authoritative state, persistence, and server-to-client synchronization.
 * Higher priorities win among active sources whose direction is not
 * {@link Direction#DOWN}; equal-priority sources are ordered by source ID.
 * Bindings support living entities only. Version 2 retains the selected frame
 * across all vanilla living movement modes. A source that throws a
 * {@link RuntimeException} during runtime resolution is quarantined for that
 * entity and treated as inactive. The first failure is logged; a matching
 * {@link #directionChanged(LivingEntity, ResourceLocation,
 * DirectionalGravitySource)} call or a rebind explicitly retries it.</p>
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
		Direction resolvedDirection = data.bindAndResolve(
				entity, sourceId, priority, source);
		if (data.updateAppliedDirection(
				effectiveDirection(entity, resolvedDirection))) {
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
	 * This also explicitly retries a matching source that was quarantined
	 * after a runtime failure.
	 */
	public static void directionChanged(LivingEntity entity,
			ResourceLocation sourceId, DirectionalGravitySource source) {
		if (entity == null || sourceId == null || source == null) {
			return;
		}
		DirectionalGravityData data = existingData(entity);
		if (data != null && data.reactivate(sourceId, source)
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
	 * Returns the direction used by core hooks.
	 */
	public static Direction getEffectiveDirection(Entity entity) {
		if (entity == null) {
			return Direction.DOWN;
		}
		DirectionalGravityData data = existingData(entity);
		return data != null ? data.appliedDirection() : Direction.DOWN;
	}

	/**
	 * Reconciles a mutable provider before an entity tick.
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
		if (!(entity instanceof LivingEntity)
				|| direction == null) {
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
