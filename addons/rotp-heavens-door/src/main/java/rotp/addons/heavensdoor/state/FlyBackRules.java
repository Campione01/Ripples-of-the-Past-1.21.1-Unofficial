package rotp.addons.heavensdoor.state;

/**
 * 1.16 TriggerFlyBackwardsPacket velocity: horizontal away from the caster,
 * normalized, times 3.25, plus 0.35 Y. Pure so the contract can run it.
 */
public final class FlyBackRules {
    public static final double STRENGTH = 3.25D;
    public static final double Y_OFFSET = 0.35D;
    private static final double MIN_LENGTH_SQ = 1.0E-6D;

    private FlyBackRules() {}

    /**
     * dx/dz are target minus caster. When the two overlap the caster's
     * horizontal look is used. Returns {x, y, z}, or null without a
     * direction.
     */
    public static double[] velocity(
            double dx, double dz, double lookX, double lookZ) {
        double x = dx;
        double z = dz;
        if (x * x + z * z <= MIN_LENGTH_SQ) {
            x = lookX;
            z = lookZ;
        }
        double lengthSq = x * x + z * z;
        if (lengthSq <= MIN_LENGTH_SQ) {
            return null;
        }
        double scale = STRENGTH / Math.sqrt(lengthSq);
        return new double[] {x * scale, Y_OFFSET, z * scale};
    }
}
