package rotp.addons.emperor.state;

public final class EmperorFireRules {
    public static final int USE_DURATION = 10;
    public static final int NORMAL_COOLDOWN = 10;
    public static final int BURST_COOLDOWN = 65;
    public static final float NORMAL_STAMINA = 40.0F;
    public static final float BURST_STAMINA = 35.0F;
    public static final float RESOLVE_COST_MULTIPLIER = 0.5F;
    public static final float NORMAL_DAMAGE = 4.0F;
    public static final float BURST_DAMAGE = 6.0F;
    public static final float RESOLVE_BURST_DAMAGE = 12.0F;
    public static final float BULLET_SPEED = 2.0F;
    public static final double AUTO_TARGET_HORIZONTAL = 19.4D;
    public static final double AUTO_TARGET_VERTICAL = 1.0D;

    private EmperorFireRules() {}

    public static boolean isShotTick(
            boolean burst, int remainingTicks) {
        if (!burst) {
            return remainingTicks % 5 == 4;
        }
        int cadence = remainingTicks % 13;
        return cadence == 1 || cadence == 3 || cadence == 5
                || cadence == 7 || cadence == 9
                || cadence == 11;
    }

    public static int cooldown(boolean burst) {
        return burst ? BURST_COOLDOWN : NORMAL_COOLDOWN;
    }

    public static float staminaCost(
            boolean burst, boolean resolve) {
        return (burst ? BURST_STAMINA : NORMAL_STAMINA)
                * (resolve ? RESOLVE_COST_MULTIPLIER : 1.0F);
    }

    public static float shotDamage(
            boolean burst, boolean resolve) {
        if (!burst) {
            return NORMAL_DAMAGE;
        }
        return resolve ? RESOLVE_BURST_DAMAGE : BURST_DAMAGE;
    }
}
