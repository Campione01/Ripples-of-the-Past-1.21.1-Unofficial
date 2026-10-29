package rotp.addons.stickyfingers.ability;

import rotp.addons.stickyfingers.StickyFingersPhase1Spec;
import net.minecraft.world.phys.AABB;

/**
 * Pure Climb Zipper rules, after the 1.16
 * StickyFingersClimbZipper perform and travelOnZipper.
 */
public final class ZipperClimbRules {
    /** 1.16 perform: rest this many body widths off the wall. */
    public static final double WALL_GAP_WIDTHS = 0.15D;
    /** 1.16 condition: the zipper wall must be this close. */
    public static final double WALL_REACH = 0.5D;
    /** How far past the body side the zipper wall is looked for. */
    public static final double WALL_PROBE = 0.2D;

    @FunctionalInterface
    public interface ZipperAt {
        boolean test(int x, int y, int z);
    }

    private ZipperClimbRules() {}

    /** 1.16: climb speed is zza * 0.5, where zza = forward * 0.98. */
    public static double verticalSpeed(float forwardImpulse) {
        double speed = forwardImpulse * 0.98F
                * StickyFingersPhase1Spec.CLIMB_ZIPPER_SPEED;
        return Math.max(-1.0D, Math.min(1.0D, speed));
    }

    /** 1.16: jump, or backing off while on the ground, lets go. */
    public static boolean releases(
            boolean jumping, boolean onGround, float forwardImpulse) {
        return jumping || onGround && forwardImpulse < 0.0F;
    }

    public static double wallGap(float bbWidth) {
        return bbWidth * WALL_GAP_WIDTHS;
    }

    /**
     * The zipper wall still covers the lower half of the body
     * (1.16 y range), checked in the body's centre column.
     */
    public static boolean isZipperColumn(
            AABB box, int wallStepX, int wallStepZ,
            ZipperAt zipperAt) {
        if ((wallStepX == 0) == (wallStepZ == 0)) {
            return false;
        }
        int minY = floor(box.minY + 0.001D);
        int maxY = Math.max(minY, floor(
                box.maxY - 0.001D - 0.5D * box.getYsize()));
        int x = wallStepX == 0
                ? floor((box.minX + box.maxX) * 0.5D)
                : floor(wallStepX > 0
                        ? box.maxX + WALL_PROBE
                        : box.minX - WALL_PROBE);
        int z = wallStepZ == 0
                ? floor((box.minZ + box.maxZ) * 0.5D)
                : floor(wallStepZ > 0
                        ? box.maxZ + WALL_PROBE
                        : box.minZ - WALL_PROBE);
        for (int y = minY; y <= maxY; y++) {
            if (!zipperAt.test(x, y, z)) {
                return false;
            }
        }
        return true;
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }
}
