package rotp.addons.heavensdoor.state;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import rotp.addons.heavensdoor.HeavensDoorAddon;

public final class CannotMoveRules {
    private CannotMoveRules() {}

    public static boolean blocksSelf(LivingEntity subject) {
        return subject.hasEffect(HeavensDoorAddon.CANNOT_MOVE);
    }

    public static boolean blocksTravel(LivingEntity subject) {
        return blocksSelf(subject) || subject.getControllingPassenger() instanceof LivingEntity rider && blocksSelf(rider);
    }

    public static void enforce(LivingEntity subject) {
        if (!blocksSelf(subject)) {
            return;
        }
        subject.xxa = 0.0F;
        subject.yya = 0.0F;
        subject.zza = 0.0F;
        subject.setJumping(false);
        subject.setSprinting(false);
        subject.setSwimming(false);
        if (subject instanceof Player player && player.isFallFlying()) {
            player.stopFallFlying();
        }
        if (subject instanceof Mob mob) {
            mob.getNavigation().stop();
            if (!mob.level().isClientSide()) {
                // Leap goals write their own velocity. Mob.tick restores the flag every 5 ticks.
                mob.goalSelector.setControlFlag(Goal.Flag.JUMP, false);
            }
        }
    }
}
