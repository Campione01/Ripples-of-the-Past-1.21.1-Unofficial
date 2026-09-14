package rotp.core.api.gravity;

import java.util.Objects;

import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.subsystems.directional_gravity.DirectionalGravityCollision;
import rotp.core.subsystems.directional_gravity.DirectionalGravityRuntime;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

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
 *
 * <p>The server applies a resolved direction only when the reoriented body has
 * a nearby collision-free placement. Otherwise its previous applied frame is
 * retained and tick reconciliation retries the request. The core synchronizes
 * applied frames and absolute anchors; client bindings never predict placement.</p>
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
		applyDirectionSafely(entity, data,
				effectiveDirection(entity, resolvedDirection));
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
		if (data.unbind(sourceId, source)) {
			applyDirectionSafely(entity, data,
					effectiveDirection(entity, data.resolve(entity)));
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
		if (data != null && data.reactivate(sourceId, source)) {
			applyDirectionSafely(entity, data,
					effectiveDirection(entity, data.resolve(entity)));
		}
	}

	/** Returns the winning provider request, which may still be awaiting clear space. */
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
	 * Reconciles a mutable provider and retries a deferred server frame before an entity tick.
	 */
	public static void reconcileEffectiveDirection(Entity entity) {
		DirectionalGravityData data = existingData(entity);
		if (data != null && !entity.level().isClientSide()) {
			applyDirectionSafely(entity, data,
					effectiveDirection(entity, data.resolve(entity)));
		}
	}

	private static void applyDirectionSafely(Entity entity,
			DirectionalGravityData data, Direction direction) {
		if (entity.level().isClientSide()
				|| data.appliedDirection() == direction
				|| DirectionalGravityRuntime.isLocalFrame(entity)) {
			return;
		}
		var position = DirectionalGravityCollision.findTransitionPosition(
				entity, data.appliedDirection(), direction);
		if (position.isEmpty()) {
			// Keep the requested providers intact; the existing tick reconciliation retries.
			return;
		}
		Vec3 velocity = entity.getDeltaMovement();
		Vec3 anchor = position.get();
		data.updateAppliedDirection(direction);
		entity.refreshDimensions();
		// Commit placement independently of transports whose teleport hook may be a no-op.
		entity.moveTo(anchor.x, anchor.y, anchor.z);
		if (entity instanceof ServerPlayer player) {
			player.connection.teleport(anchor.x, anchor.y, anchor.z,
					player.getYRot(), player.getXRot());
		}
		entity.setDeltaMovement(velocity);
		clearPreviousSupport(entity);
		// Absolute player teleports clear client velocity; this frame restores world momentum.
		entity.syncData(ModDataAttachmentTypes.DIRECTIONAL_GRAVITY.get());
	}

	static void clearPreviousSupport(Entity entity) {
		entity.setOnGround(false);
		entity.horizontalCollision = false;
		entity.verticalCollision = false;
		entity.verticalCollisionBelow = false;
		entity.minorHorizontalCollision = false;
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
