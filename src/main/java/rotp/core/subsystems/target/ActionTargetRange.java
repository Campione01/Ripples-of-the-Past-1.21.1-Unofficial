package rotp.core.subsystems.target;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The 1.16 range rule of an action with an entity target (Action.checkRangeAndTarget).
 */
public final class ActionTargetRange {
	private ActionTargetRange() {}

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
}
