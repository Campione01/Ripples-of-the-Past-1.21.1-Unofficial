package rotp.addons.wonderofu.ability;

import javax.annotation.Nullable;

import rotp.core.api.stand.StandSkillWalls;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.HitResultUtil;
import rotp.core.subsystems.target.StandPrecisionRay;
import rotp.core.util.functions.MathUtil;

import rotp.addons.wonderofu.entity.WonderOfUStandEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;

final class CalamityRangedTargeting {
    static final double MAX_RANGE = 100.0D;

    private CalamityRangedTargeting() {}

    /**
     * What the Stand's precision ray met and the point 1.16 read from it with
     * RayTraceResult.getLocation(): an entity's clip point, the point on a
     * block's outline, or the end of the ray.
     */
    record Aim(ActionTarget target, Vec3 location) {}

    /**
     * 1.16 StandEntity.aimWithStandOrUser(100, current). A Stand that follows
     * its user and holds no entity task target first turns from its own
     * rotation toward what the user's precision ray meets (an entity's eyes, a
     * block's centre), by at most getAttackSpeed() / 16 * 18 degrees on each
     * axis (MCUtil.rotateTowards); with an entity task target it is turned to
     * that entity's eyes instead (StandEntityTask.rotateStand). Then the
     * Stand's own precision ray decides, from its eyes: players and Stands
     * first, then hostile mobs, then the rest, hitboxes grown by the Stand's
     * precision. A manually controlled Stand keeps its rotation.
     */
    static Aim aimWithStandOrUser(
            WonderOfUStandEntity stand,
            @Nullable LivingEntity user,
            Object skill,
            ActionTarget current) {
        boolean wallsHide =
                StandSkillWalls.wallsHideEntities(stand.level(), skill);
        if (!stand.isManuallyControlled() && user != null) {
            if (current.getType() == ActionTarget.TargetType.ENTITY
                    && current.getEntity() != null) {
                stand.lookAtPos(current.getEntity().getEyePosition(), true);
            }
            else {
                Aim userAim = precisionTrace(user, stand, wallsHide);
                ActionTarget met = userAim.target();
                boolean anotherEntity =
                        met.getType() == ActionTarget.TargetType.ENTITY;
                if (anotherEntity
                        || current.getType() == ActionTarget.TargetType.EMPTY
                                && met.getType()
                                        != ActionTarget.TargetType.EMPTY) {
                    Vec3 targetPos = targetPos(met);
                    if (targetPos != null) {
                        rotateTowards(stand, targetPos);
                    }
                }
            }
        }
        return precisionTrace(stand, stand, wallsHide);
    }

    // 1.16 ExplodeVariations: the explosion variations need an entity
    static Vec3 resolveEntityHitPosition(
            WonderOfUStandEntity stand,
            @Nullable LivingEntity user,
            Object skill,
            ActionTarget current) {
        Aim aim = aimWithStandOrUser(stand, user, skill, current);
        return aim.target().getType() == ActionTarget.TargetType.ENTITY
                ? aim.location()
                : null;
    }

    // 1.16 ActionTarget.getTargetPos(true)
    @Nullable
    private static Vec3 targetPos(ActionTarget target) {
        return switch (target.getType()) {
            case ENTITY -> target.getEntity() != null
                    ? target.getEntity().getEyePosition()
                    : null;
            case BLOCK -> Vec3.atCenterOf(target.getBlockPos());
            default -> null;
        };
    }

    // 1.16 MCUtil.rotateTowards
    private static void rotateTowards(
            WonderOfUStandEntity stand, Vec3 targetPos) {
        float maxAngle = (float) stand.getAttackSpeed() / 16.0F * 18.0F;
        Vec3 targetVec = targetPos.subtract(stand.getEyePosition());
        float yRot = stand.getYRot();
        float xRot = stand.getXRot();
        float turnedYRot = yRot + Mth.clamp(
                Mth.degreesDifference(
                        yRot, MathUtil.yRotDegFromVec(targetVec)),
                -maxAngle, maxAngle);
        float turnedXRot = xRot + Mth.clamp(
                Mth.degreesDifference(
                        xRot, MathUtil.xRotDegFromVec(targetVec)),
                -maxAngle, maxAngle);
        stand.setYRot(turnedYRot % 360.0F);
        stand.setXRot(turnedXRot % 360.0F);
        stand.setYHeadRot(turnedYRot);
    }

    // 1.16 precisionRayTrace(aiming, 100): friendly fire allowed
    private static Aim precisionTrace(
            Entity aiming, WonderOfUStandEntity stand, boolean wallsHide) {
        ActionTarget target = StandPrecisionRay.trace(
                aiming, stand, MAX_RANGE, 0.0D, true, wallsHide);
        Vec3 from = aiming.getEyePosition();
        Vec3 look = aiming.getLookAngle();
        Vec3 rayEnd = from.add(look.normalize().scale(MAX_RANGE));
        Vec3 location = switch (target.getType()) {
            case ENTITY -> target.getClipPos().orElse(from);
            case BLOCK -> HitResultUtil.clipBlocks(
                    new ClipContext(
                            from,
                            rayEnd,
                            ClipContext.Block.OUTLINE,
                            ClipContext.Fluid.NONE,
                            aiming),
                    aiming.level()).getLocation();
            default -> from.add(look.scale(MAX_RANGE));
        };
        return new Aim(target, location);
    }
}
