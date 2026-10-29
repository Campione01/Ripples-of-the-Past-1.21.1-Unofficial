package rotp.addons.kingcrimson;

/**
 * Epitaph's foresight. In the manga Epitaph shows its user the coming seconds (about ten of them), and what it shows
 * is certain to happen: only King Crimson erasing that time lets its user escape it. The 1.16 addon only kept the
 * reaction to a foreseen attack. While Epitaph is armed the port also shows the vision itself: where nearby creatures
 * will be and where incoming projectiles will fly.
 *
 * <p>A creature's foreseen future is a fate: once Epitaph has shown where it will go, it goes there and cannot change
 * its mind on the way. The fate ends when the foreseen moment arrives, when something physically throws the creature
 * off its course, or when the Epitaph user erases the time it belonged to.</p>
 */
public final class KingCrimsonEpitaphForesightSpec {
    /**
     * How far ahead the vision reaches at each resolve level (index = level). Epitaph unlocks at level 1 with a glimpse
     * of one second; at the top level it reaches the manga's ten seconds.
     */
    private static final int[] FORESIGHT_TICKS_BY_RESOLVE = {20, 20, 60, 120, 200};
    /** Epitaph stays armed at least this much longer than its vision reaches, so a foreseen fate can play out. */
    public static final int WINDOW_MARGIN_TICKS = 40;
    public static final int REFRESH_TICKS = 2;
    public static final double RADIUS = 24.0D;
    /** A fated creature stays bound while it is this close to the Epitaph user, even outside the vision radius. */
    public static final double FATE_KEEP_RADIUS = 40.0D;
    public static final int MAX_CREATURES = 12;
    public static final int MAX_PROJECTILES = 12;
    /** Creatures whose foreseen position is closer than this to where they stand are not shown. */
    public static final double MIN_VISIBLE_SHIFT = 0.5D;
    public static final int PATH_POINT_INTERVAL_TICKS = 2;
    /** Most points traced for one foreseen flight; long visions trace sparser points. */
    public static final int MAX_FLIGHT_POINTS = 48;
    /** A fate that has not arrived this long after its foreseen moment is over. */
    public static final int FATE_GRACE_TICKS = 20;
    /** A creature thrown this far off its fated course is released and foreseen anew. */
    public static final double FATE_BREAK_DISTANCE = 6.0D;
    /**
     * A creature released from its fate (fulfilled, broken, or its time erased) gets this long to decide anew before
     * Epitaph foresees it again, so the next vision shows its new intent rather than the course it was bound to.
     */
    public static final int REDECIDE_TICKS = 10;
    /** Creatures moving without a navigation path are only bound for this long: their course is plain momentum. */
    public static final int UNGUIDED_FATE_TICKS = 20;
    /** A client vision image that is not refreshed for this long has left the foreseen future. */
    public static final int VISION_STALE_TICKS = 6;
    /** Opacity of a vision image, so the creature it foresees stays visible behind it. */
    public static final float VISION_ALPHA = 0.55F;
    /** A vision image closer to the camera than this is not drawn (a creature foreseen to reach the user). */
    public static final double VISION_CAMERA_CLEAR_DISTANCE = 0.3D;
    /** Over this much further distance a vision image fades back in. */
    public static final double VISION_CAMERA_FADE_DISTANCE = 1.5D;

    private KingCrimsonEpitaphForesightSpec() {}

    public static int foresightTicks(int resolveLevel) {
        int level = Math.max(0, Math.min(resolveLevel, FORESIGHT_TICKS_BY_RESOLVE.length - 1));
        return FORESIGHT_TICKS_BY_RESOLVE[level];
    }

    /** How long one activation of Epitaph stays armed at a resolve level. */
    public static int windowTicks(int resolveLevel) {
        // floor: 1.16 KCConfig epitaphDuration (default EPITAPH_WINDOW_TICKS)
        return Math.max(KingCrimsonConfig.epitaphDurationTicks(), foresightTicks(resolveLevel) + WINDOW_MARGIN_TICKS);
    }
}
