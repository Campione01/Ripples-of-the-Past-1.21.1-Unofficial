package rotp.addons.littlefeet.scale;

public final class LittleFeetScaleMath {
    public static final float MIN_SCALE = 0.15F;
    public static final float TARGET_SHRINK_STEP = 0.05F;
    public static final int TARGET_SHRINK_INTERVAL = 160;
    public static final float SELF_SLOW_STEP = 0.02F;

    private LittleFeetScaleMath() {}

    public static float clampScale(float scale) {
        return Math.max(MIN_SCALE, Math.min(1.0F, scale));
    }

    public static float nextTargetShrink(float currentScale) {
        return clampScale(currentScale - TARGET_SHRINK_STEP);
    }

    public static float nextSelfScale(
            float currentScale, boolean shrinking) {
        return clampScale(
                currentScale
                        + (shrinking
                                ? -SELF_SLOW_STEP
                                : SELF_SLOW_STEP));
    }

    public static double knockbackMultiplier(double scale) {
        return Math.max(
                0.0D,
                (clampScale((float) scale) - MIN_SCALE)
                        / (1.0D - MIN_SCALE));
    }

    public static double scaledExtent(
            double unscaledExtent, float scale) {
        return unscaledExtent * clampScale(scale);
    }
}
