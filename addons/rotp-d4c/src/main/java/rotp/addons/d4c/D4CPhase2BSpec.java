package rotp.addons.d4c;

public final class D4CPhase2BSpec {
    public static final int EFFECT_REFRESH_TICKS = 40;
    // eyes Night Vision stays above vanilla's 200-tick fade window
    public static final int NIGHT_VISION_REFRESH_TICKS = 300;
    public static final int NIGHT_VISION_REFRESH_BELOW_TICKS = 220;
    public static final int LOVE_TRAIN_DURATION_TICKS = 18_000;
    public static final int LIGHT_WALL_DURATION_TICKS = 600;
    public static final int LIGHT_WALL_FLUID_CLEAR_INTERVAL_TICKS = 5;
    public static final int LIGHT_WALL_ENTITY_PUSH_INTERVAL_TICKS = 3;
    public static final int LIGHT_WALL_RANGE = 40;
    public static final int LIGHT_WALL_MIN_Y_OFFSET = -2;
    public static final int LIGHT_WALL_MAX_Y_OFFSET = 3;
    public static final int LIGHT_WALL_PROJECTILE_MIN_Y_OFFSET = -5;
    public static final int LIGHT_WALL_PROJECTILE_MAX_Y_OFFSET = 25;
    public static final double LIGHT_WALL_PUNISH_HORIZONTAL_RANGE = 10.0D;
    public static final double LIGHT_WALL_PUNISH_VERTICAL_RANGE = 5.0D;
    public static final float LIGHT_WALL_PUNISH_DAMAGE = 4.0F;
    public static final int SPINE_COOLDOWN_TICKS = 1_200;
    public static final int MAX_MINIONS = 6;
    public static final double MINION_RADIUS = 50.0D;
    // 1.16 aa/aa/ad.java:52: minion_ai_control wanted an own minion in the
    // user's box grown by 64
    public static final double MINION_AI_CONTROL_RANGE = 64.0D;
    // 1.16 ak/aa/ab/aa.java:51: the chosen mode reached the own minions in
    // the box 1000 blocks around the user
    public static final double MINION_AI_APPLY_RANGE = 1_000.0D;
    // 1.16 al/aa.java:43, :171, :199-201
    public static final int MARK_CHECK_INTERVAL_TICKS = 20;
    public static final double MARK_MAX_DISTANCE = 20.0D;
    public static final int CLONE_LIFESPAN_TICKS = 1_200;
    public static final float ANNIHILATION_KILL_THRESHOLD = 50.0F;
    public static final float ANNIHILATION_DAMAGE_FRACTION = 0.5F;
    public static final double PARALLEL_ITEM_ENTITY_DAMAGE_RADIUS = 6.0D;
    public static final double PARALLEL_ITEM_INVENTORY_DAMAGE_RADIUS = 5.0D;
    public static final double DEATH_DROP_PROTECTION_RANGE = 5.0D;
    public static final long DEATH_DROP_PROTECTION_MILLIS = 1_000L;
    // 1.16 action ctor overrides the registered 150 with 20
    public static final float RAPID_STAMINA = 20.0F;
    // 1.16 ac.java:68: the hits of the task ticks, after the punch of
    // StandEntityLightAttack.standPerform, which added 0.1 to the meter
    public static final int RAPID_HITS = 6;
    public static final float RAPID_PERFORM_FINISHER = 0.1F;
    public static final float RAPID_DAMAGE_MULTIPLIER = 1.3F;
    public static final float RAPID_FINISHER_PER_HIT = 0.05F;
    public static final double RAPID_KNOCKBACK = 0.1D;
    public static final double RAPID_FINAL_BASE_KNOCKBACK = 1.5D;
    public static final double RAPID_FINAL_HORIZONTAL_KNOCKBACK = 2.0D;
    public static final double RAPID_FINAL_UPWARD_KNOCKBACK = 0.8D;
    public static final float HAND_CHOP_DAMAGE_MULTIPLIER = 1.3F;
    public static final int HAND_CHOP_BLOOD_PARTICLES = 40;
    public static final int PORTAL_ARM_TICKS = 16;
    public static final int PORTAL_LIFESPAN_TICKS = 100;
    public static final int COLT_AMMO = 6;
    public static final int COLT_RELOAD_TICKS = 60;

    private D4CPhase2BSpec() {}
}
