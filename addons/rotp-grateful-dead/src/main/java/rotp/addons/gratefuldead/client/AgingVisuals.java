package rotp.addons.gratefuldead.client;

/**
 * Canonical Grateful Dead material color curves and tint math.
 */
public final class AgingVisuals {
    public static final int WITHER_COLOR = 0x6B4520;

    private AgingVisuals() {}

    public static float blockCurve(float progress) {
        return (float) Math.pow(
                clamp(progress),
                1.85D);
    }

    public static float entityCurve(float progress) {
        return (float) Math.pow(
                clamp(progress),
                1.4D);
    }

    public static int tintEntityArgb(
            int originalArgb, float progress) {
        float severity = entityCurve(progress);
        if (severity <= 0.0F) {
            return originalArgb;
        }
        int alpha = originalArgb >>> 24;
        float red = (originalArgb >> 16 & 0xFF) / 255.0F;
        float green = (originalArgb >> 8 & 0xFF) / 255.0F;
        float blue = (originalArgb & 0xFF) / 255.0F;
        float luminance =
                0.299F * red
                        + 0.587F * green
                        + 0.114F * blue;
        float grayMix = clamp(
                0.08F * severity
                        + 0.12F * severity * severity);
        red = lerp(grayMix, red, luminance);
        green = lerp(grayMix, green, luminance);
        blue = lerp(grayMix, blue, luminance);

        float pallor = clamp(
                0.04F + luminance * 0.28F);
        float agedRed = clamp(pallor + 0.22F);
        float agedGreen = clamp(pallor + 0.10F);
        float agedBlue = clamp(pallor - 0.10F);
        float agedMix = 0.92F * severity;
        red = lerp(agedMix, red, agedRed);
        green = lerp(agedMix, green, agedGreen);
        blue = lerp(agedMix, blue, agedBlue);

        float dryness = 0.05F * severity;
        red += dryness * 0.85F;
        green += dryness * 0.30F;
        blue -= dryness;
        return alpha << 24
                | Math.round(clamp(red) * 255.0F) << 16
                | Math.round(clamp(green) * 255.0F) << 8
                | Math.round(clamp(blue) * 255.0F);
    }

    public static int tintBlockRgb(
            int originalRgb, float curvedProgress) {
        float aging = clamp(curvedProgress);
        int red = originalRgb >> 16 & 0xFF;
        int green = originalRgb >> 8 & 0xFF;
        int blue = originalRgb & 0xFF;
        int agedRed = (int) (red * (1.0F - aging)
                + 107.0F * aging);
        int agedGreen = (int) (green * (1.0F - aging)
                + 69.0F * aging);
        int agedBlue = (int) (blue * (1.0F - aging)
                + 32.0F * aging);
        return agedRed << 16 | agedGreen << 8 | agedBlue;
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static float lerp(
            float amount, float start, float end) {
        return start + amount * (end - start);
    }
}
