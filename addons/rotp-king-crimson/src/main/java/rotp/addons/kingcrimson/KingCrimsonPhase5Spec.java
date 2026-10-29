package rotp.addons.kingcrimson;

public final class KingCrimsonPhase5Spec {
    public static final float TIME_SKIP_STAMINA = 400.0F;
    public static final int TIME_SKIP_COOLDOWN_TICKS = 60;
    public static final int TIME_SKIP_IMPLIED_TICKS = 200;
    public static final int TIME_SKIP_INVULNERABLE_TICKS = 10;
    public static final double TIME_SKIP_MAX_DISTANCE = 32.0D;
    public static final double TIME_SKIP_STEP = 0.5D;
    public static final double TIME_SKIP_PRESENTATION_RADIUS = 192.0D;

    public static final float TIME_ERASE_ACTIVATION_STAMINA = 250.0F;
    public static final float TIME_ERASE_STAMINA_PER_TICK = 0.5F;
    public static final int TIME_ERASE_DURATION_TICKS = 200;
    // 1.16 KCConfig.timeErasePresentEntityDelay (60) timed only the stationary "present" afterimage and the
    // rewind it marked at erase end; the port keeps neither (owner decision 2026-09-23, manga logic).
    public static final int TIME_ERASE_MOVING_AFTERIMAGE_DELAY_TICKS = 10;
    public static final double TIME_ERASE_AFTERIMAGE_RADIUS = 192.0D;
    public static final double TIME_ERASE_PRESENTATION_RADIUS = 192.0D;
    public static final double TIME_ERASE_DECOY_TARGET_RADIUS = 16.0D;
    public static final int TIME_ERASE_MINIMUM_COOLDOWN_TICKS = 120;
    public static final float TIME_ERASE_COOLDOWN_MULTIPLIER = 2.0F;
    // 1.16 KCConfig defaults; only timeEraseCooldown ever reached the player (KingCrimsonTimeErase :245-252)
    public static final boolean TIME_ERASE_DYNAMIC_COOLDOWN = true;
    public static final int TIME_ERASE_FIXED_COOLDOWN_TICKS = 200;
    public static final double TIME_ERASE_MOVEMENT_MULTIPLIER = 1.0D;
    public static final double TIME_ERASE_MAX_FALL_SPEED = -0.15D;
    public static final int TIME_ERASE_TRANSITION_MILLIS = 375;
    public static final int TIME_ERASE_TRANSITION_FRAME_MILLIS = 23;
    public static final int TIME_ERASE_SKY_PORTAL_LAYERS = 13;
    public static final long TIME_ERASE_SKY_RANDOM_SEED = 31100L;

    public static final String TIME_SKIP_POST_CHAIN_PATH =
            "shaders/post/time_skip.json";

    private KingCrimsonPhase5Spec() {}
}
