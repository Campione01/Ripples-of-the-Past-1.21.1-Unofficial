package rotp.addons.stickyfingers.state;

import java.util.Set;

import javax.annotation.Nullable;

import rotp.core.subsystems.target.ActionTargetRange;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class DetachedPartTargeting {
    private DetachedPartTargeting() {}

    // 1.16 TargetHitPart.getHitTarget: where the aiming entity's look,
    // carried as far as JojoModUtil.getDistance, is on the target's height
    public static DetachedPartType.Region aimedRegion(
            Entity target, Entity aimingEntity) {
        double distance = ActionTargetRange.getDistance(
                aimingEntity, target.getBoundingBox());
        double aimY = aimingEntity.getEyePosition(1.0F)
                .add(aimingEntity.getLookAngle().scale(distance))
                .y - target.getY();
        double height = target.getBbHeight();
        if (aimY < height * 0.375D) {
            return DetachedPartType.Region.LEGS;
        }
        if (aimY < height * 0.75D) {
            return DetachedPartType.Region.TORSO_ARMS;
        }
        return DetachedPartType.Region.HEAD;
    }

    // 1.16 TargetHitPart.getPartCenter
    public static Vec3 partCenter(
            DetachedPartType.Region region, LivingEntity target) {
        return switch (region) {
            case HEAD -> new Vec3(
                    target.getX(), target.getY(1.0D), target.getZ());
            case TORSO_ARMS -> new Vec3(
                    target.getX(), target.getY(0.7D), target.getZ())
                    .add(new Vec3(target.getBbWidth() * 0.375F, 0.0D, 0.0D)
                            .yRot((180.0F - target.getYRot())
                                    * Mth.DEG_TO_RAD));
            case LEGS -> new Vec3(
                    target.getX(), target.getY(0.0D), target.getZ());
        };
    }

    // 1.16 StickyFingersUnzipBodyPart.standTickRecovery (:104-106, :122-124,
    // :151-153): the head on top of the target, an arm or a leg at its feet
    public static Vec3 spawnPosition(
            DetachedPartType part, LivingEntity target) {
        return part.isHead()
                ? target.position().add(0.0D, target.getBbHeight(), 0.0D)
                : target.position();
    }

    // 1.16 StickyFingersUnzipBodyPart.standTickRecovery (:100-156): an arm by
    // the user's random, in its order of rolls (with both arms on, the left
    // at the first roll, else the right at the second, else none); a leg by
    // the target's main arm, the leg on its other side while that one is on
    @Nullable
    public static DetachedPartType selectAvailable(
            DetachedPartType.Region region,
            Set<DetachedPartType> detached,
            HumanoidArm targetMainArm,
            RandomSource random) {
        return switch (region) {
            case HEAD -> detached.contains(DetachedPartType.HEAD)
                    ? null
                    : DetachedPartType.HEAD;
            case TORSO_ARMS -> {
                boolean leftOff = detached.contains(DetachedPartType.LEFT_ARM);
                boolean rightOff =
                        detached.contains(DetachedPartType.RIGHT_ARM);
                if (leftOff && rightOff) {
                    yield null;
                }
                if (!leftOff && (random.nextBoolean() || rightOff)) {
                    yield DetachedPartType.LEFT_ARM;
                }
                if (rightOff || !random.nextBoolean() && !leftOff) {
                    yield null;
                }
                yield DetachedPartType.RIGHT_ARM;
            }
            case LEGS -> {
                boolean leftOff = detached.contains(DetachedPartType.LEFT_LEG);
                boolean rightOff =
                        detached.contains(DetachedPartType.RIGHT_LEG);
                if (leftOff && rightOff) {
                    yield null;
                }
                if (targetMainArm == HumanoidArm.RIGHT) {
                    yield leftOff
                            ? DetachedPartType.RIGHT_LEG
                            : DetachedPartType.LEFT_LEG;
                }
                yield rightOff
                        ? DetachedPartType.LEFT_LEG
                        : DetachedPartType.RIGHT_LEG;
            }
        };
    }
}
