package rotp.addons.harvest.entity;

// 1.16 HarvestTeleportGoal leash, kept pure so the contract can pin it.
public final class HarvestOwnerLeash {
    public static final double CLOSE_RANGE = 7.0D;
    // 1.16: min(stand max range 200, 50)
    public static final double FAR_RANGE = 50.0D;
    public static final double TELEPORT_MARGIN = 10.0D;
    public static final double TELEPORT_SPREAD = 5.0D;
    // Path budget must cover the far leash before it teleports.
    public static final double PATH_RANGE = FAR_RANGE + TELEPORT_MARGIN;

    public enum Action {
        NONE,
        PATH,
        TELEPORT
    }

    private HarvestOwnerLeash() {}

    // 1.16 skipped ATTACKING and going-to-place; item collection
    // outranked the leash goal, so SEARCH/RETURN_ITEM are free too.
    public static boolean applies(HarvestMemberMode mode) {
        return mode == HarvestMemberMode.FOLLOW
                || mode == HarvestMemberMode.STAY_WITH;
    }

    public static double range(boolean closeFormation) {
        return closeFormation ? CLOSE_RANGE : FAR_RANGE;
    }

    public static Action action(
            double distance, boolean closeFormation) {
        double range = range(closeFormation);
        if (distance > range + TELEPORT_MARGIN) {
            return Action.TELEPORT;
        }
        if (distance > range / 2.0D) {
            return Action.PATH;
        }
        return Action.NONE;
    }

    // 1.16 landing: anchor +-5 blocks on X and Z.
    public static double spread(double anchor, double random01) {
        return anchor + TELEPORT_SPREAD
                - 2.0D * TELEPORT_SPREAD * random01;
    }
}
