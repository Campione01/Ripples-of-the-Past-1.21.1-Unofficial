package rotp.addons.blood_bottle;

public final class BloodBottleEconomy {
    private BloodBottleEconomy() {}

    public static float refillCost(
            float maxResource, int fillBottleMultiplier) {
        float max = finiteNonNegative(maxResource);
        int multiplier = clamp(fillBottleMultiplier, 1, 1000);
        return max / 10.0F / 100.0F * multiplier;
    }

    public static float bottleValue(
            float maxResource,
            int bottleDrainPenalty,
            int fillBottleMultiplier) {
        float max = finiteNonNegative(maxResource);
        int penalty = clamp(bottleDrainPenalty, 0, 100);
        int multiplier = clamp(fillBottleMultiplier, 1, 1000);
        return (max / 10.0F - max / 1000.0F * penalty)
                / 100.0F * multiplier;
    }

    public static boolean canDrink(
            float currentResource,
            float maxResource,
            int maxBottleHungerValue) {
        float current = finiteNonNegative(currentResource);
        float max = finiteNonNegative(maxResource);
        int threshold = clamp(maxBottleHungerValue, 1, 100);
        return current <= max / 100.0F * threshold;
    }

    private static float finiteNonNegative(float value) {
        return Float.isFinite(value) ? Math.max(value, 0.0F) : 0.0F;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }
}
