package rotp.addons.thelock.ability;

import java.util.Optional;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.thelock.TheLockAddon;
import rotp.addons.thelock.entity.TheLockStandEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class TheLockTargeting {
    public static final double TARGET_RANGE = 30.0D;
    public static final double TARGET_INFLATION = 1.0D;

    private TheLockTargeting() {}

    public static LivingEntity owningUser(Power<?> context) {
        LivingEntity user = context != null ? context.getUser() : null;
        return user != null
                && TheLockAddon.isTheLockUser(user)
                ? user
                : null;
    }

    public static LivingEntity raytraceStateTarget(
            LivingEntity user) {
        return stateTarget(raytraceRawTarget(user));
    }

    public static LivingEntity stateTarget(
            LivingEntity target) {
        if (target instanceof StandEntity stand
                && stand.getUser() != null) {
            return stand.getUser();
        }
        return target;
    }

    public static LivingEntity raytraceRawTarget(
            LivingEntity user) {
        Level level = user.level();
        Vec3 start = user.getEyePosition();
        Vec3 direction = user.getLookAngle();
        Vec3 fullEnd = start.add(direction.scale(TARGET_RANGE));
        BlockHitResult blockHit = level.clip(new ClipContext(
                start,
                fullEnd,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                user));
        Vec3 end = blockHit.getType() == HitResult.Type.BLOCK
                ? blockHit.getLocation()
                : fullEnd;

        AABB search = user.getBoundingBox()
                .expandTowards(end.subtract(start))
                .inflate(TARGET_INFLATION);
        LivingEntity best = null;
        double bestDistance = start.distanceToSqr(end);
        for (Entity candidate : level.getEntities(
                user, search, TheLockTargeting::canTarget)) {
            LivingEntity living = (LivingEntity) candidate;
            AABB hitbox = living.getBoundingBox().inflate(
                    living.getPickRadius() + TARGET_INFLATION);
            Optional<Vec3> intersection = hitbox.contains(start)
                    ? Optional.of(start)
                    : hitbox.clip(start, end);
            if (intersection.isEmpty()) {
                continue;
            }
            double distance =
                    start.distanceToSqr(intersection.get());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = living;
            }
        }
        return best;
    }

    private static boolean canTarget(Entity entity) {
        return entity instanceof LivingEntity living
                && living.isAlive()
                && !(living instanceof TheLockStandEntity)
                && (!(living instanceof StandEntity stand)
                        || stand.getUser() != null);
    }
}
