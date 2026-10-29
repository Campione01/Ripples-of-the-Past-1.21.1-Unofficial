package rotp.addons.sexpistols.content.revolver.entity;

public final class RevolverBallistics {
    public enum TerminalImpact {
        RICOCHETED,
        DISCARD,
        BULLET_HOLE
    }

    private RevolverBallistics() {}

    public static TerminalImpact terminalImpact(
            boolean ricochetAttempted,
            boolean ricochetSucceeded) {
        if (ricochetSucceeded) {
            return TerminalImpact.RICOCHETED;
        }
        return ricochetAttempted
                ? TerminalImpact.DISCARD
                : TerminalImpact.BULLET_HOLE;
    }

    public static float scaledDamage(
            float baseDamage, double multiplier) {
        return (float) (baseDamage * multiplier);
    }

    public static float distanceScaledDamage(
            double travelDistance,
            double closeDamageEnd,
            double stableDamageEnd,
            double minimumDamage,
            double multiplier,
            float closeBaseDamage,
            float stableBaseDamage,
            double farDamageDropRate) {
        float closeDamage = scaledDamage(
                closeBaseDamage, multiplier);
        float stableDamage = scaledDamage(
                stableBaseDamage, multiplier);
        double damage;
        if (closeDamageEnd > 0.0D
                && travelDistance <= closeDamageEnd) {
            double progress = clamp(
                    travelDistance / closeDamageEnd,
                    0.0D,
                    1.0D);
            double eased = 1.0D
                    - Math.pow(1.0D - progress, 3.0D);
            damage = closeDamage
                    - (closeDamage - stableDamage) * eased;
        }
        else if (travelDistance <= stableDamageEnd) {
            damage = stableDamage;
        }
        else {
            double farDistance = travelDistance - stableDamageEnd;
            damage = minimumDamage
                    + (stableDamage - minimumDamage)
                            * Math.exp(-farDistance
                                    * farDamageDropRate);
        }
        return (float) clamp(
                damage, minimumDamage, closeDamage);
    }

    private static double clamp(
            double value, double minimum, double maximum) {
        return Math.min(maximum, Math.max(minimum, value));
    }
}
