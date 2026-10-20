package rotp.core.subsystems.target;

import java.util.List;
import java.util.function.Predicate;

import rotp.core.api.stand.StandSkillWalls;
import rotp.core.mechanics.resolve.ResolveCounter;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.util.functions.AttributeUtil;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * 1.16 StandEntity.precisionRayTrace: every pickable entity on the aiming ray within reach, its hitbox grown by the
 * Stand's precision, else the block (outline shapes, so grass stops it); of several entities a player or a Stand comes
 * first, then a hostile mob, then the rest.
 */
public final class StandPrecisionRay {

	private StandPrecisionRay() {}

	/** 1.16 precisionRayTrace(aimingEntity): from the aiming user, at its reach, friendly fire allowed. */
	public static ActionTarget trace(LivingEntity user, StandEntity stand) {
		return trace(user, stand, reach(user, stand), 0.0D, true, false);
	}

	/**
	 * 1.16 precisionRayTrace(aimingEntity, reachDistance, rayTraceInflate, friendlyFire).
	 * @param wallsHideEntities 1.16 picked entities through blocks; with this, as the port's other aims do, an entity
	 * behind a block with a collision shape on the ray is not picked
	 */
	public static ActionTarget trace(Entity aiming, StandEntity stand, double reach, double rayTraceInflate,
			boolean friendlyFire, boolean wallsHideEntities) {
		Predicate<Entity> filter = targetFilter(stand, friendlyFire);
		List<ActionTarget> targets = clipTargets(aiming, reach, filter, rayTraceInflate, stand.getPrecision(),
				wallsHideEntities);
		return closestWithPriority(targets);
	}

	/**
	 * The aim of a Stand user without the aim assist: what the aiming entity's crosshair ray meets first within reach,
	 * by the entities' own boxes, with no priority; blocks stop it.
	 */
	public static ActionTarget crosshair(Entity aiming, StandEntity stand, double reach, boolean friendlyFire) {
		return crosshair(aiming, stand, reach, friendlyFire, StandSkillWalls.wallsHideEntities(aiming));
	}

	/** @param wallsHideEntities false: an entity on the crosshair line behind a block is met before the block */
	public static ActionTarget crosshair(Entity aiming, StandEntity stand, double reach, boolean friendlyFire,
			boolean wallsHideEntities) {
		Predicate<Entity> filter = targetFilter(stand, friendlyFire).and(entity -> !entity.isSpectator() && entity.isPickable());
		return HitResultUtil.clip(aiming.getEyePosition(), aiming.getLookAngle(), reach, reach, aiming.level(), filter,
				aiming, 0, wallsHideEntities);
	}

	/** 1.16 StandEntity.canTarget: any entity that is alive but the Stand, its user and the Stand's own projectiles. */
	public static Predicate<Entity> canTarget(StandEntity stand) {
		return entity -> entity != stand && entity != stand.getUser() && entity.isAlive()
				&& !(entity instanceof Projectile projectile && projectile.getOwner() == stand);
	}

	// 1.16 StandEntity.canTarget, and canHarm without friendly fire
	private static Predicate<Entity> targetFilter(StandEntity stand, boolean friendlyFire) {
		Predicate<Entity> filter = canTarget(stand);
		return friendlyFire ? filter : filter.and(stand::canAttackEntity);
	}

	/**
	 * HitResultUtil.clipMultipleTargets (1.16 JojoModUtil.rayTraceMultipleEntities: pickable entities nearest first,
	 * else the block by its outline), except that an entity behind a block with a collision shape on the ray is not
	 * picked, as the port's other aims do, unless the skill in progress goes through walls
	 * ({@link StandSkillWalls#wallsHideEntities(Entity)}).
	 */
	public static List<ActionTarget> clipTargetsBeforeWalls(Entity aiming, double reach, Predicate<Entity> entityFilter,
			double rayTraceInflate, double standPrecision) {
		return clipTargets(aiming, reach, entityFilter, rayTraceInflate, standPrecision,
				StandSkillWalls.wallsHideEntities(aiming));
	}

	/** @param wallsHideEntities false: 1.16 JojoModUtil.rayTraceMultipleEntities, entities through blocks */
	public static List<ActionTarget> clipTargets(Entity aiming, double reach, Predicate<Entity> entityFilter,
			double rayTraceInflate, double standPrecision, boolean wallsHideEntities) {
		if (!wallsHideEntities) {
			return HitResultUtil.clipMultipleTargets(aiming, reach, entityFilter, rayTraceInflate, standPrecision);
		}
		Vec3 from = aiming.getEyePosition();
		Vec3 to = from.add(aiming.getLookAngle().normalize().scale(reach));
		double wallDistanceSqr = wallDistanceSqr(aiming, from, to);
		List<ActionTarget> targets = HitResultUtil.clipMultipleEntities(aiming, reach, entityFilter, rayTraceInflate,
				standPrecision).stream().filter(target -> isBeforeWall(target, from, to, wallDistanceSqr)).toList();
		return targets.isEmpty() ? HitResultUtil.clipMultipleTargets(aiming, reach, entity -> false, 0.0D, 0.0D) : targets;
	}

	// 1.16 keeps the priority of the previous entity when the next one fits no category
	public static ActionTarget closestWithPriority(List<ActionTarget> targets) {
		if (targets.isEmpty()) {
			return ActionTarget.EMPTY;
		}
		if (targets.size() == 1) {
			return targets.get(0);
		}
		ActionTarget[] closestWithPriority = new ActionTarget[4];
		int priority = 3;
		for (ActionTarget target : targets) {
			Entity entity = target.getEntity();
			if (entity instanceof LivingEntity) {
				if (entity instanceof Player || entity instanceof StandEntity) {
					priority = 0;
				}
				else if (ResolveCounter.attackingTargetGivesResolve(entity)) {
					priority = 1;
				}
			}
			else if (entity != null) {
				priority = 2;
			}
			if (closestWithPriority[priority] == null) {
				closestWithPriority[priority] = target;
			}
		}
		for (ActionTarget target : closestWithPriority) {
			if (target != null) {
				return target;
			}
		}
		return targets.get(0);
	}

	// ServerPlayer.CREATIVE_BLOCK_INTERACTION_RANGE_MODIFIER, put on or taken off on the player's next tick
	private static final ResourceLocation CREATIVE_BLOCK_RANGE = ResourceLocation.withDefaultNamespace("creative_mode_block_range");

	/**
	 * 1.16 StandEntity.getAimDistance: the aiming user's REACH_DISTANCE, 5 for a player in either game mode. The 1.21
	 * attribute is the Survival pick range, half a block less, plus a Creative bonus that is left out here, so the
	 * reach does not depend on the game mode or on when the game last updated that bonus.
	 */
	public static double reach(LivingEntity user, StandEntity stand) {
		AttributeInstance reach = user.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
		if (reach == null) {
			return stand.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
		}
		return AttributeUtil.calcValueWithoutModifiers(reach, CREATIVE_BLOCK_RANGE) + 0.5D;
	}

	private static double wallDistanceSqr(Entity aiming, Vec3 from, Vec3 to) {
		BlockHitResult wall = HitResultUtil.clipBlocks(new ClipContext(from, to,
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.of(aiming)), aiming.level());
		if (wall.getType() == HitResult.Type.MISS) {
			return Double.MAX_VALUE;
		}
		Vec3 wallPos = new AABB(wall.getBlockPos()).clip(from, to).orElse(wall.getLocation());
		return from.distanceToSqr(wallPos);
	}

	// As HitResultUtil.clip measures an entity: where the ray meets its own box, or the nearest point of that box
	private static boolean isBeforeWall(ActionTarget target, Vec3 from, Vec3 to, double wallDistanceSqr) {
		Entity entity = target.getEntity();
		if (entity == null) {
			return true;
		}
		AABB box = entity.getBoundingBox().inflate(entity.getPickRadius());
		Vec3 point;
		if (box.contains(from)) {
			point = from;
		}
		else {
			Vec3 precisionClip = target.getClipPos().orElse(from);
			point = box.clip(from, to).orElseGet(() -> new Vec3(
					Mth.clamp(precisionClip.x, box.minX, box.maxX),
					Mth.clamp(precisionClip.y, box.minY, box.maxY),
					Mth.clamp(precisionClip.z, box.minZ, box.maxZ)));
		}
		return from.distanceToSqr(point) < wallDistanceSqr;
	}
}
