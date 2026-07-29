package com.github.standobyte.jojo.subsystems.directional_gravity;

import com.github.standobyte.jojo.api.gravity.DirectionalGravityApi;
import com.github.standobyte.jojo.api.gravity.DirectionalGravityTransforms;
import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = JojoMod.MOD_ID)
public final class DirectionalGravityRuntime {
	private DirectionalGravityRuntime() {}

	public static LocalFrame enterLocalFrame(LivingEntity entity) {
		DirectionalGravityFrameAccess access = access(entity);
		int depth = access.jojo_ripples$localFrameDepth();
		Direction direction = depth > 0
				? access.jojo_ripples$localFrameDirection()
				: DirectionalGravityApi
						.getEffectiveDirection(entity);
		if (direction == Direction.DOWN) {
			return LocalFrame.INACTIVE;
		}
		if (depth == 0) {
			access.jojo_ripples$setLocalFrameDirection(direction);
			access.jojo_ripples$setLastLocalMovement(Vec3.ZERO);
		}
		entity.setDeltaMovement(enterFrameVelocity(
				direction, entity.getDeltaMovement(), depth));
		access.jojo_ripples$setLocalFrameDepth(depth + 1);
		return new LocalFrame(entity);
	}

	public static boolean isLocalFrame(Entity entity) {
		return entity instanceof DirectionalGravityFrameAccess access
				&& access.jojo_ripples$localFrameDepth() > 0;
	}

	public static Direction localFrameDirection(Entity entity) {
		if (!isLocalFrame(entity)) {
			return Direction.DOWN;
		}
		return access(entity).jojo_ripples$localFrameDirection();
	}

	public static Vec3 lastLocalMovement(Entity entity) {
		return isLocalFrame(entity)
				? access(entity).jojo_ripples$lastLocalMovement()
				: Vec3.ZERO;
	}

	public static void recordLocalMovement(Entity entity,
			Vec3 movement) {
		if (isLocalFrame(entity)) {
			access(entity).jojo_ripples$setLastLocalMovement(
					movement);
		}
	}

	public static void setWorldMoveAdapterActive(Entity entity,
			boolean active) {
		access(entity).jojo_ripples$setWorldMoveAdapterActive(active);
	}

	public static boolean isWorldMoveAdapterActive(Entity entity) {
		return entity instanceof DirectionalGravityFrameAccess access
				&& access.jojo_ripples$isWorldMoveAdapterActive();
	}

	@SubscribeEvent
	public static void onEntityTick(EntityTickEvent.Pre event) {
		DirectionalGravityApi.reconcileEffectiveDirection(
				event.getEntity());
	}

	private static DirectionalGravityFrameAccess access(Entity entity) {
		return (DirectionalGravityFrameAccess) entity;
	}

	static Vec3 enterFrameVelocity(Direction direction,
			Vec3 velocity, int currentDepth) {
		return currentDepth == 0
				? DirectionalGravityTransforms.toLocal(
						direction, velocity)
				: velocity;
	}

	static Vec3 exitFrameVelocity(Direction direction,
			Vec3 velocity, int remainingDepth) {
		return remainingDepth == 0
				? DirectionalGravityTransforms.toWorld(
						direction, velocity)
				: velocity;
	}

	public static final class LocalFrame implements AutoCloseable {
		private static final LocalFrame INACTIVE =
				new LocalFrame(null);

		private LivingEntity entity;

		private LocalFrame(LivingEntity entity) {
			this.entity = entity;
		}

		public boolean isActive() {
			return entity != null;
		}

		@Override
		public void close() {
			LivingEntity closing = entity;
			if (closing == null) {
				return;
			}
			entity = null;
			DirectionalGravityFrameAccess access = access(closing);
			int depth = access.jojo_ripples$localFrameDepth() - 1;
			if (depth < 0) {
				throw new IllegalStateException(
						"Directional gravity frame underflow");
			}
			access.jojo_ripples$setLocalFrameDepth(depth);
			Direction direction =
					access.jojo_ripples$localFrameDirection();
			closing.setDeltaMovement(exitFrameVelocity(
					direction, closing.getDeltaMovement(), depth));
			if (depth == 0) {
				access.jojo_ripples$setLocalFrameDirection(
						Direction.DOWN);
				access.jojo_ripples$setLastLocalMovement(
						Vec3.ZERO);
			}
		}
	}
}
