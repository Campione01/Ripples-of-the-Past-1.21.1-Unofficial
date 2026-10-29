package rotp.addons.heavensdoor.state;

import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.init.ModStatusEffects;

import net.minecraft.world.entity.LivingEntity;

public final class BookStateRules {
    public static final int BASE_DURATION_TICKS = 200;
    public static final int POWERED_DURATION_TICKS = 300;
    public static final int WEAKENED_POWERED_DURATION_TICKS = 400;
    public static final int UNPOWERED_DURATION_TICKS = 600;
    public static final int DEVELOPED_POWERED_DURATION_TICKS = 400;
    public static final int DEVELOPED_WEAKENED_DURATION_TICKS = 600;
    public static final int DEVELOPED_UNPOWERED_DURATION_TICKS = 900;
    public static final int MAX_POWERED_DURATION_TICKS = 600;
    public static final int MAX_WEAKENED_DURATION_TICKS = 800;
    public static final int MAX_UNPOWERED_DURATION_TICKS = 1200;
    public static final float WEAKENED_TARGET_HEALTH_FRACTION = 0.65F;
    public static final float COMMAND_RELEASE_HEALTH_FRACTION = 0.08F;
    public static final int RESOLVE_COUNTER_DURATION_DIVISOR = 2;
    public static final int VANISH_NEARBY_DURATION_TICKS = 600;
    public static final int CANNOT_MOVE_DURATION_TICKS = 120;
    public static final int UNTIMED_COMMAND_DURATION = -1;
    public static final double FORGOT_RUN_FACTOR = 0.8D;
    public static final double FORGOT_SPRINT_SWIM_FACTOR = 0.5D;
    public static final double FORGOT_SWIM_FACTOR = 0.9D;
    public static final int MAX_PAGES = 10;
    public static final int TEAR_HOLD_TICKS = 20;
    public static final int TEAR_COOLDOWN_TICKS = 30;
    public static final float TEAR_STAMINA_COST = 100.0F;
    public static final double ACTION_RANGE = 4.5D;
    public static final double OWNER_RANGE = 12.0D;
    public static final double RETURN_PAGE_RANGE = 24.0D;
    public static final int FORGOTTEN_EFFECT_DURATION = 3600;

    private BookStateRules() {}

    public static int durationFor(
            LivingEntity owner, LivingEntity target) {
        BookStrength strength = strengthFor(owner, target);
        return strength.durationTicks();
    }

    public static int amplifierFor(
            LivingEntity owner, LivingEntity target) {
        return strengthFor(owner, target).amplifier();
    }

    public static BookStrength strengthFor(LivingEntity owner, LivingEntity target) {
        StandPower ownerPower = owner != null ? StandPower.get(owner) : null;
        StandPower targetStand = StandPower.get(target);
        PlayerPower targetPlayerPower = PlayerPower.get(target);
        boolean hasPower = targetStand != null
                && targetStand.hasPower()
                || targetPlayerPower != null
                && targetPlayerPower.hasPower();
        return strengthFor(
                ownerPower != null && ownerPower.hasPower() ? ownerPower.getResolveLevel() : 0,
                ownerPower != null && ownerPower.hasPower() ? ownerPower.getMaxResolveLevel() : 0,
                hasPower,
                target.getHealth() <= target.getMaxHealth() * WEAKENED_TARGET_HEALTH_FRACTION,
                target.hasEffect(ModStatusEffects.RESOLVE));
    }

    public static int resolveTier(int level, int maxLevel) {
        if (maxLevel <= 0 || level <= 0) {
            return 0;
        }
        if (level >= maxLevel) {
            return 3;
        }
        if ((long) level * 4 >= (long) maxLevel * 3) {
            return 2;
        }
        return (long) level * 2 >= maxLevel ? 1 : 0;
    }

    public static BookStrength strengthFor(
            int level, int maxLevel, boolean powered, boolean weakened, boolean targetResolve) {
        BookStrength strength = switch (resolveTier(level, maxLevel)) {
            case 1 -> !powered ? new BookStrength(UNPOWERED_DURATION_TICKS, 2)
                    : weakened ? new BookStrength(WEAKENED_POWERED_DURATION_TICKS, 1)
                    : new BookStrength(POWERED_DURATION_TICKS, 0);
            case 2 -> !powered ? new BookStrength(DEVELOPED_UNPOWERED_DURATION_TICKS, 2)
                    : weakened ? new BookStrength(DEVELOPED_WEAKENED_DURATION_TICKS, 2)
                    : new BookStrength(DEVELOPED_POWERED_DURATION_TICKS, 1);
            case 3 -> !powered ? new BookStrength(MAX_UNPOWERED_DURATION_TICKS, 2)
                    : weakened ? new BookStrength(MAX_WEAKENED_DURATION_TICKS, 2)
                    : new BookStrength(MAX_POWERED_DURATION_TICKS, 2);
            default -> new BookStrength(BASE_DURATION_TICKS, 0);
        };
        return targetResolve ? new BookStrength(
                strength.durationTicks() / RESOLVE_COUNTER_DURATION_DIVISOR, 0) : strength;
    }

    public static boolean restrictedCommandsReleased(boolean alive, float health, float maxHealth, boolean creative) {
        return !alive || !creative && health <= maxHealth * COMMAND_RELEASE_HEALTH_FRACTION;
    }

    public record BookStrength(int durationTicks, int amplifier) {}

    public static int clampPages(int pages) {
        return Math.clamp(pages, 0, MAX_PAGES);
    }
}
