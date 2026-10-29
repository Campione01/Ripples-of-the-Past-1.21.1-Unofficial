package rotp.addons.weatherreport.ability;

import java.util.List;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import rotp.core.mechanics.resolve.ResolveCounter;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.HitResultUtil;
import rotp.core.util.functions.MathUtil;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 1.16 JojoModUtil.rayTraceMultipleEntities, StandEntity.precisionRayTrace and aimWithStandOrUser, read through
 * RayTraceResult.getLocation(): every pickable entity on the whole ray comes before any block, so walls do not hide them
 * (owner decision of 2026-10-15 for Lightning, Cold Wind and Fugu Rain); with no entity on the ray, the point where the
 * ray meets a block's outline shape, or the end of the ray.
 */
final class WeatherReportAim {
    private WeatherReportAim() {}

    record Hit(@Nullable Entity entity, @Nullable BlockHitResult block, Vec3 location) {}

    /** The target the client sent with the press: the Stand's aim, which takes the user's when it met nothing. */
    static ActionTarget pressTarget(LivingEntity stand) {
        var aim = LivingComponentAction.getAim(stand);
        return aim != null ? aim.getTarget() : ActionTarget.EMPTY;
    }

    /** 1.16 JojoModUtil.rayTrace(aiming, range, entityFilter).getLocation(): the nearest entity, then the block. */
    static Vec3 hitLocation(Entity aiming, double range, Predicate<Entity> entityFilter) {
        return trace(aiming, aiming.getLookAngle(), range, entityFilter, 0.0D, false).location();
    }

    /** 1.16 stand.precisionRayTrace(aiming, range): the Stand's filter and precision, players and Stands first. */
    static Hit precisionTrace(Entity aiming, @Nullable StandEntity stand, @Nullable LivingEntity user, double range) {
        return trace(aiming, aiming.getLookAngle(), range, canTarget(stand, user),
                stand != null ? stand.getPrecision() : 0.0D, true);
    }

    /**
     * 1.16 stand.aimWithStandOrUser(range, ActionTarget.EMPTY): a Stand that follows its user first turns from the
     * user's rotation toward what the user's precision ray meets (an entity's eyes, a block's centre), by at most
     * getAttackSpeed() / 16 * 18 degrees on each axis; then the Stand's own precision ray decides.
     */
    static Vec3 aimWithStandOrUser(StandEntity stand, @Nullable LivingEntity user, double range) {
        return standAim(stand, user, range, true).location();
    }

    /**
     * {@link #aimWithStandOrUser} as a hit. With turnStand false the Stand keeps its rotation and the ray takes the turned
     * one: the port checks ability conditions every tick, where 1.16 checked them only on a press or a hold.
     */
    static Hit standAim(StandEntity stand, @Nullable LivingEntity user, double range, boolean turnStand) {
        float yRot = stand.getYRot();
        float xRot = stand.getXRot();
        if (!stand.isManuallyControlled() && user != null) {
            Hit userAim = precisionTrace(user, stand, user, range);
            Vec3 targetPos = userAim.entity() != null ? userAim.entity().getEyePosition()
                    : userAim.block() != null ? Vec3.atCenterOf(userAim.block().getBlockPos())
                    : null;
            if (targetPos != null) {
                // 1.16 MCUtil.rotateTowards
                float maxAngle = (float) stand.getAttackSpeed() / 16.0F * 18.0F;
                Vec3 targetVec = targetPos.subtract(stand.getEyePosition());
                float turnedYRot = yRot + Mth.clamp(Mth.degreesDifference(yRot, MathUtil.yRotDegFromVec(targetVec)),
                        -maxAngle, maxAngle);
                float turnedXRot = xRot + Mth.clamp(Mth.degreesDifference(xRot, MathUtil.xRotDegFromVec(targetVec)),
                        -maxAngle, maxAngle);
                yRot = turnedYRot % 360.0F;
                xRot = turnedXRot % 360.0F;
                if (turnStand) {
                    stand.setYRot(yRot);
                    stand.setXRot(xRot);
                    stand.setYHeadRot(turnedYRot);
                }
            }
        }
        return trace(stand, stand.calculateViewVector(xRot, yRot), range, canTarget(stand, user),
                stand.getPrecision(), true);
    }

    // 1.16 StandEntity.canTarget
    static Predicate<Entity> canTarget(@Nullable StandEntity stand, @Nullable LivingEntity user) {
        return entity -> entity != stand
                && entity != user
                && entity.isAlive()
                && !(stand != null && entity instanceof Projectile projectile && projectile.getOwner() == stand);
    }

    private static Hit trace(Entity aiming, Vec3 look, double range, Predicate<Entity> entityFilter,
            double standPrecision, boolean categoryPriority) {
        Vec3 start = aiming.getEyePosition();
        List<ActionTarget> entities = HitResultUtil.clipMultipleEntities(start, look, range, aiming.level(), aiming,
                entityFilter, 0.0D, standPrecision);
        if (!entities.isEmpty()) {
            ActionTarget target = categoryPriority ? closestWithPriority(entities) : entities.get(0);
            return new Hit(target.getEntity(), null, target.getClipPos().orElse(start));
        }
        BlockHitResult block = aiming.level().clip(new ClipContext(
                start,
                start.add(look.normalize().scale(range)),
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                aiming));
        return new Hit(null, block.getType() == HitResult.Type.BLOCK ? block : null, block.getLocation());
    }

    // 1.16 precisionRayTrace: players and Stands, then hostile mobs, then non-living entities; an entity that fits no
    // category keeps the priority of the one before it.
    private static ActionTarget closestWithPriority(List<ActionTarget> targets) {
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
            else {
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
}
