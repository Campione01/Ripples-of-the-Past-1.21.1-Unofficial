package rotp.addons.d4c.entity;

import java.util.UUID;

// 1.16 ag/ab.java (player clone) and ah/aa.java (non-player clone) effects
public final class D4CAnnihilationCloneEffects {
    public static final int START_PARTICLES = 30;
    public static final int MINION_SLOW_TICK = 1;
    public static final int ANNIHILATE_SOUND_TICK = 10;
    public static final int MINION_SLOW_DURATION = 600;
    public static final int MINION_SLOW_AMPLIFIER = 79;
    public static final double MINION_RANGE = 50.0D;
    public static final int VANISH_PARTICLES = 20;
    public static final int DEATH_BURST_PARTICLES = 50;
    public static final double DEATH_BURST_SPREAD = 4.0D;
    public static final float DEATH_EXPLODE_VOLUME = 2.0F;

    private D4CAnnihilationCloneEffects() {}

    // ag/ab.java:230 is 5.0, ah/aa.java:157 is 1.5
    public static float startVolume(boolean playerClone) {
        return playerClone ? 5.0F : 1.5F;
    }

    // ag/ab.java:267 is 2.0, ah/aa.java:234 is 1.0
    public static float annihilateVolume(boolean playerClone) {
        return playerClone ? 2.0F : 1.0F;
    }

    // ag/ab.java:375 is 4.0, ah/aa.java:359 is 2.0
    public static float finalExplodeVolume(boolean playerClone) {
        return playerClone ? 4.0F : 2.0F;
    }

    // ag/ab.java:203-211 particle budget from the target's box
    public static int burstBase(
            double width, double depth, double height) {
        double size = Math.max(width, depth);
        return Math.max(20, Math.min(100,
                (int) (size * size * height * 15.0D)));
    }

    // ag/ab.java:369-373: double budget over 1.5x the padded box
    public static int finalBurstCount(
            double width, double depth, double height) {
        return burstBase(width, depth, height) * 2;
    }

    public static double finalSpreadXZ(double width, double depth) {
        return (Math.max(width, depth) + 1.0D) * 1.5D;
    }

    public static double finalSpreadY(double height) {
        return (height + 0.5D) * 1.5D;
    }

    // ag/ab.java:382-395 player clone poofs; ah/aa.java:55-57 does not
    public static boolean vanishesWithPoof(boolean playerClone) {
        return playerClone;
    }

    // ah/aa.java:82-86: target died mid-annihilation
    public static boolean burstsOnLostTarget(
            boolean playerClone,
            int annihilation,
            boolean targetPresent,
            boolean targetAlive) {
        return !playerClone && annihilation >= 0
                && targetPresent && !targetAlive;
    }

    // ag/ab.java:244 hits every minion; ah/aa.java:178 spares the user's
    public static boolean minionShares(
            boolean playerClone, UUID cloneOwner, UUID minionOwner) {
        return playerClone
                || minionOwner == null
                || !minionOwner.equals(cloneOwner);
    }

    // ah/aa.java:224-228: non-player 2.5% share stops at 1 health
    public static boolean minionShareHoldsAtOne(
            boolean playerClone,
            boolean earlyPhase,
            float health,
            float damage) {
        return !playerClone && earlyPhase && health - damage <= 1.0F;
    }
}
