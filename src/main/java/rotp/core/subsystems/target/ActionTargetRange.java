package rotp.core.subsystems.target;

import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The 1.16 range rule of an action with an entity target (Action.checkRangeAndTarget).
 */
public final class ActionTargetRange {
	/** 1.16 Action.getMaxRangeSqEntityTarget and getMaxRangeSqBlockTarget, unless an action overrides them. */
	public static final double DEFAULT_ENTITY_RANGE_SQ = 64.0D;
	public static final double DEFAULT_BLOCK_RANGE_SQ = 100.0D;

	private ActionTargetRange() {}

	/** 1.16 StandAction.getPerformer: the Stand while it is out, its user otherwise. */
	public static LivingEntity standPerformer(LivingEntity user) {
		StandPower power = user != null ? StandPower.get(user) : null;
		StandEntity stand = power != null ? power.getSummonedStandEntity() : null;
		return stand != null ? stand : user;
	}

	/**
	 * 1.16 JojoModUtil.getDistance: from the entity's eyes to where a line towards the box's centre, taken at the
	 * entity's proportional eye height, enters the box, less half the entity's width. -1 when that line misses.
	 */
	public static double getDistance(Entity entity, AABB targetAabb) {
		Vec3 startPos = entity.getEyePosition(1.0F);
		if (targetAabb.contains(startPos)) {
			return 0.0D;
		}
		Vec3 endPos = new Vec3(
				Mth.lerp(0.5D, targetAabb.minX, targetAabb.maxX),
				Mth.lerp(entity.getBbHeight() == 0.0F ? 0.0D : entity.getEyeHeight() / entity.getBbHeight(),
						targetAabb.minY, targetAabb.maxY),
				Mth.lerp(0.5D, targetAabb.minZ, targetAabb.maxZ));
		return targetAabb.clip(startPos, endPos)
				.map(clipVec -> startPos.distanceTo(clipVec) - entity.getBbWidth() / 2)
				.orElse(-1.0D);
	}

	/**
	 * 1.16 Action.checkRangeAndTarget with getMaxRangeSqEntityTarget() == maxRangeSq:
	 * the squared range is quartered when the performer has no line of sight to the target.
	 */
	public static boolean isEntityWithinRange(LivingEntity performer, Entity target, double maxRangeSq) {
		double rangeSq = performer.hasLineOfSight(target) ? maxRangeSq : maxRangeSq / 4.0D;
		double distance = getDistance(performer, target.getBoundingBox());
		return distance * distance <= rangeSq;
	}

	/**
	 * 1.16 Action.checkRangeAndTarget for a block target: the distance to the bounds of the block's shape, with no
	 * line of sight rule. A block without a shape is out of range.
	 */
	public static boolean isBlockWithinRange(LivingEntity performer, Level level, BlockPos pos, double maxRangeSq) {
		VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
		if (shape.isEmpty()) {
			return false;
		}
		double distance = getDistance(performer, shape.bounds().move(pos));
		return distance * distance <= maxRangeSq;
	}

	public static boolean isTargetWithinRange(LivingEntity performer, ActionTarget target, Level level) {
		return isTargetWithinRange(performer, target, level, DEFAULT_ENTITY_RANGE_SQ, DEFAULT_BLOCK_RANGE_SQ);
	}

	/** 1.16 Action.checkRangeAndTarget for the target under the crosshair; nothing is in range of an empty one. */
	public static boolean isTargetWithinRange(LivingEntity performer, ActionTarget target, Level level,
			double entityRangeSq, double blockRangeSq) {
		if (performer == null || target == null || target.isEmpty(level)) {
			return false;
		}
		return switch (target.getType()) {
			case ENTITY -> isEntityWithinRange(performer, target.getEntity(), entityRangeSq);
			case BLOCK -> isBlockWithinRange(performer, level, target.getBlockPos(), blockRangeSq);
			default -> false;
		};
	}

	/**
	 * 1.16 Action.checkRangeAndTarget answers an entity target beyond the range with "target_too_far", before the
	 * action looks at what kind of entity it is. False without an entity target.
	 */
	public static boolean isEntityTargetOutOfRange(LivingEntity performer, ActionTarget target, double maxRangeSq) {
		if (target == null || target.getType() != ActionTarget.TargetType.ENTITY) {
			return false;
		}
		Entity entity = target.getMainEntity();
		return entity != null && !isEntityWithinRange(performer, entity, maxRangeSq);
	}
}
