package rotp.addons.sexpistols.content.revolver.casing;

public record CasingEjectionSpec(
        long id,
        long spawnGameTime,
        double x,
        double y,
        double z,
        double xVelocity,
        double yVelocity,
        double zVelocity,
        int lifetime,
        float size,
        int landingSound,
        float landingPitch) {
    public static final int MAX_CASING_COUNT = 6;
    public static final int MIN_LIFETIME_TICKS = 34;
    public static final int MAX_LIFETIME_TICKS = 49;
    public static final float MIN_SIZE = 0.115F;
    public static final float MAX_SIZE = 0.145F;

    public CasingEjectionSpec {
        requireFinite(x, "x");
        requireFinite(y, "y");
        requireFinite(z, "z");
        requireFinite(xVelocity, "xVelocity");
        requireFinite(yVelocity, "yVelocity");
        requireFinite(zVelocity, "zVelocity");
        if (lifetime < MIN_LIFETIME_TICKS
                || lifetime > MAX_LIFETIME_TICKS) {
            throw new IllegalArgumentException(
                    "Casing lifetime is outside the donor range");
        }
        if (!Float.isFinite(size)
                || size < MIN_SIZE
                || size > MAX_SIZE) {
            throw new IllegalArgumentException(
                    "Casing size is outside the donor range");
        }
        if (landingSound < 0 || landingSound > 3) {
            throw new IllegalArgumentException(
                    "Casing landing sound must be in [0, 3]");
        }
        if (!Float.isFinite(landingPitch)
                || landingPitch < 0.92F
                || landingPitch > 1.14F) {
            throw new IllegalArgumentException(
                    "Casing landing pitch is outside the donor range");
        }
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    "Casing " + name + " must be finite");
        }
    }
}
