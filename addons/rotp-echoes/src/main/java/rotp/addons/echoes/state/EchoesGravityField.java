package rotp.addons.echoes.state;

// 092 GravityFieldHandler and GravityItemEvents numbers, free of game classes
public final class EchoesGravityField {
    // 092 range is 2.5 + 0.3 * count, and its count stops at 1
    public static final double FIELD_RANGE = 2.5D + 0.3D;
    public static final double PINNED_HORIZONTAL = 0.3D;
    public static final double PINNED_VERTICAL = -0.1D;
    public static final double WATER_HORIZONTAL = 0.1D;
    public static final double WATER_VERTICAL = -0.8D;
    // 092 GravityItemEvents pressure: range and period
    public static final double PRESSURE_RANGE = 3.0D;
    public static final long PRESSURE_PERIOD = 20L;

    private EchoesGravityField() {}

    public static boolean inField(double distance) {
        return distance < FIELD_RANGE;
    }

    public static boolean inPressure(double distance) {
        return distance < PRESSURE_RANGE;
    }

    // Blocks under frozen entities break once a second
    public static boolean pressureTick(long gameTime) {
        return gameTime % PRESSURE_PERIOD == 0L;
    }

    // Air and bedrock never break
    public static boolean pressureBreaks(boolean air, boolean bedrock) {
        return !air && !bedrock;
    }

    // Motion of a living entity inside a carrier's field
    public static double[] pinnedMotion(double x, double y, double z) {
        return new double[] {
                x * PINNED_HORIZONTAL,
                PINNED_VERTICAL,
                z * PINNED_HORIZONTAL};
    }

    // 092: 85% per infused stack, floor 20%
    public static double carrierMultiplier(int count) {
        return Math.max(0.2D, 1.0D - count * 0.15D);
    }

    // Carrier motion; in water the carrier sinks instead
    public static double[] carrierMotion(
            double x, double y, double z, int count, boolean inWater) {
        if (inWater) {
            return new double[] {
                    x * WATER_HORIZONTAL,
                    WATER_VERTICAL,
                    z * WATER_HORIZONTAL};
        }
        double multiplier = carrierMultiplier(count);
        return new double[] {x * multiplier, y * multiplier, z * multiplier};
    }
}
