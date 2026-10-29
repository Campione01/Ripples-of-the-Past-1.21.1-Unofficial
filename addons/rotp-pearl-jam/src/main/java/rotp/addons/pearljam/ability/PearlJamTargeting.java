package rotp.addons.pearljam.ability;

import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.HitResultUtil;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

final class PearlJamTargeting {
    private PearlJamTargeting() {}

    static LivingEntity findTargetOrSelf(
            LivingEntity user, Level level, double range) {
        ActionTarget synced = syncedTarget(user, level);
        LivingEntity target =
                resolveLiving(user, synced, level, range);
        if (target != null) {
            return target;
        }

        ActionTarget clipped = HitResultUtil.clip(
                user.getEyePosition(),
                user.getLookAngle(),
                range,
                range,
                level,
                entity -> entity instanceof LivingEntity
                        && entity != user,
                user,
                0.0D);
        target = resolveLiving(user, clipped, level, range);
        return target != null ? target : user;
    }

    static LivingEntity resolveLiving(
            LivingEntity user,
            ActionTarget target,
            Level level,
            double range) {
        if (target == null
                || target.isEmpty(level)
                || !HitResultUtil.isTargetWithinRange(
                        target, user, level, range, range)) {
            return null;
        }
        Entity entity = target.getMainEntity();
        return entity instanceof LivingEntity living
                && living != user
                && living.isAlive()
                && !living.isRemoved()
                ? living
                : null;
    }

    private static ActionTarget syncedTarget(
            LivingEntity user, Level level) {
        var aim = LivingComponentAction.getAim(user);
        if (aim == null || aim.getTarget() == null) {
            return ActionTarget.EMPTY;
        }
        return aim.getTarget().resolveEntityId(level);
    }
}
