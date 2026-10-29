package rotp.addons.tusk.state;

public final class TuskActRules {
    public static final int ACT_1 = 0;
    public static final int ACT_2 = 1;
    public static final int ACT_3 = 2;
    public static final int ACT_4 = 3;
    public static final int ACT_2_RESOLVE_LEVEL = 1;
    public static final int ACT_3_RESOLVE_LEVEL = 2;
    public static final int ACT_4_RESOLVE_LEVEL = 3;
    public static final int MAX_CHARGE_TICKS = 60;
    public static final int TICKS_PER_SPIN_CHARGE = 5;
    public static final int MAX_SPIN_CHARGE =
            MAX_CHARGE_TICKS / TICKS_PER_SPIN_CHARGE;
    public static final int LIFETIME_PER_SPIN_CHARGE = 10;

    private TuskActRules() {}

    public static boolean isActUnlocked(
            int currentAct, int targetAct, int resolveLevel) {
        if (targetAct < ACT_1
                || targetAct > ACT_4
                || currentAct < ACT_1
                || currentAct > ACT_4) {
            return false;
        }
        if (targetAct <= currentAct) {
            return true;
        }
        int requiredResolve = switch (targetAct) {
            case ACT_2 -> ACT_2_RESOLVE_LEVEL;
            case ACT_3 -> ACT_3_RESOLVE_LEVEL;
            case ACT_4 -> ACT_4_RESOLVE_LEVEL;
            default -> Integer.MAX_VALUE;
        };
        return targetAct == currentAct + 1
                && resolveLevel >= requiredResolve;
    }

    public static int spinChargeForTicks(int chargeTicks) {
        int boundedTicks = Math.max(
                0, Math.min(chargeTicks, MAX_CHARGE_TICKS));
        return boundedTicks / TICKS_PER_SPIN_CHARGE;
    }

    public static int wormholeLifetimeForSpinCharge(int spinCharge) {
        return Math.max(0, spinCharge)
                * LIFETIME_PER_SPIN_CHARGE;
    }
}
