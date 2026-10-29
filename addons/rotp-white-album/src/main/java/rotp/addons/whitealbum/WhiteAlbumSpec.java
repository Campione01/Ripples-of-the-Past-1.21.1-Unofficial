package rotp.addons.whitealbum;

public final class WhiteAlbumSpec {
    public static final double POWER = 14.0D;
    public static final double SPEED = 10.0D;
    public static final double EFFECTIVE_RANGE = 5.0D;
    public static final double MAX_RANGE = 10.0D;
    public static final double DURABILITY = 18.0D;
    public static final double PRECISION = 3.0D;

    public static final float CONTROLLER_WIDTH = 0.065F;
    public static final float CONTROLLER_HEIGHT = 0.195F;

    public static final double FREEZE_RADIUS = 5.0D;
    public static final int FREEZE_COOLDOWN_TICKS = 30;
    public static final float FREEZE_STAMINA_PER_TICK = 8.0F;
    public static final int FREEZE_WATER_RADIUS = 4;
    public static final double FREEZE_WATER_RAY_RANGE = 8.0D;
    public static final int ICE_MELT_MIN_TICKS = 20;
    public static final int ICE_MELT_MAX_TICKS = 40;

    public static final float FREEZE_FLOOR_STAMINA_PER_TICK = 1.0F;
    public static final int FREEZE_FLOOR_COOLDOWN_TICKS = 30;
    public static final int FREEZE_FLOOR_RADIUS = 3;

    public static final float UNFREEZE_STAMINA = 60.0F;
    public static final int UNFREEZE_COOLDOWN_TICKS = 40;
    public static final int UNFREEZE_RADIUS = 9;

    // The summoned-Stand base regeneration (1.16 EntityStandType and the
    // core StandType both use 1.5), kept while the guard is held.
    public static final float GUARD_STAMINA_REGEN = 1.5F;
    public static final float GUARD_STAMINA_PER_DAMAGE = 7.5F;
    public static final float GUARD_FLAT_REDUCTION = 9.0F;
    public static final float GUARD_BREAK_POST_REDUCTION_DAMAGE = 15.0F;
    public static final int GUARD_COOLDOWN_TICKS = 60;
    public static final int GUARD_BREAK_LOCK_TICKS = 20;
    public static final float GUARD_USER_WALK_SPEED = 0.5F;

    public static final double AURA_SCAN_RADIUS = 11.0D;
    public static final double AURA_EFFECT_RADIUS = 10.0D;
    public static final int AURA_EFFECT_TICKS = 20;
    public static final double ARMOR_TARGET = 22.0D;
    public static final double MAX_ARMOR_BONUS = 10.0D;

    // Donor GameplayHandler.onEntityTick charged 2.5 with no phase filter,
    // so 1.16 ran it for both PlayerTickEvent phases: 5 per server tick
    // against a single regeneration step. The port charges once per tick.
    public static final float GENTLY_WEEPS_STAMINA_PER_TICK = 5.0F;
    public static final float GENTLY_WEEPS_DEFLECT_STAMINA = 14.0F;
    public static final int GENTLY_WEEPS_COOLDOWN_TICKS = 20;
    public static final double MIN_DEFLECT_SPEED = 0.5D;

    private WhiteAlbumSpec() {}
}
