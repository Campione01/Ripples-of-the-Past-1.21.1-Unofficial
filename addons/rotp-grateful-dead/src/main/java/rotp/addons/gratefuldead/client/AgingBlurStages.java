package rotp.addons.gratefuldead.client;

final class AgingBlurStages {
    static final float START_PROGRESS = 0.35F;
    static final float PROGRESS_RANGE = 0.65F;
    static final float MEDIUM_STRENGTH = 0.33F;
    static final float STRONG_STRENGTH = 0.66F;

    private AgingBlurStages() {}

    static float strengthForProgress(float progress) {
        if (progress <= START_PROGRESS) {
            return 0.0F;
        }
        return Math.max(
                0.0F,
                Math.min(
                        1.0F,
                        (progress - START_PROGRESS)
                                / PROGRESS_RANGE));
    }

    static int levelForProgress(float progress) {
        return levelForStrength(
                strengthForProgress(progress));
    }

    static int levelForStrength(float strength) {
        return strength >= STRONG_STRENGTH
                ? 3
                : strength >= MEDIUM_STRENGTH
                        ? 2
                        : strength > 0.0F ? 1 : 0;
    }
}
