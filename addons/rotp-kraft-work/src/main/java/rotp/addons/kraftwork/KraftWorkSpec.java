package rotp.addons.kraftwork;

public final class KraftWorkSpec {
    public static final double POWER = 14.0D;
    public static final double SPEED = 14.0D;
    public static final double MAX_SPEED = 16.0D;
    public static final double EFFECTIVE_RANGE = 1.0D;
    public static final double MAX_RANGE = 2.0D;
    public static final double DURABILITY = 10.0D;
    public static final double PRECISION = 2.0D;
    public static final double MAX_PRECISION = 4.5D;
    public static final float ENTITY_WIDTH = 0.65F;
    public static final float ENTITY_HEIGHT = 1.95F;

    public static final double LOCK_RANGE = 15.0D;
    public static final double LOCK_RANGE_SQR =
            LOCK_RANGE * LOCK_RANGE;
    public static final double BLOCK_MARKER_RANGE_SQR = 140.0D;
    // 1.16 KraftWorkReleaseProjectile: the user's box inflated by 1.
    public static final double RELEASE_PROJECTILE_REACH = 1.0D;
    public static final double ENERGY_SPEED_PER_CHARGE = 0.143D;
    public static final double MAX_RELEASE_SPEED = 3.15D;
    public static final double ARROW_DAMAGE_PER_CHARGE = 0.0091D;
    public static final int LOCK_TARGET_HOLD_TICKS = 10;
    public static final int ENERGY_PER_TICK = 10;
    public static final int LOCK_EFFECT_DURATION = 19_999_980;
    public static final float PLACE_STAMINA = 10.0F;
    public static final float LOCK_TARGET_STAMINA = 10.0F;
    public static final float RELEASE_TARGET_STAMINA = 1.0F;
    public static final float ENERGY_STAMINA = 1.0F;
    public static final float RELEASE_STAMINA = 1.0F;
    public static final float BLOCKING_ITEMS_STAMINA = 30.0F;
    // 1.16 KraftWorkBlock, squared distances from the guarding Stand's centre.
    public static final double GUARD_DEFLECT_RANGE_SQR = 5.5D;
    public static final double GUARD_LOCK_RANGE_SQR = 9.0D;
    public static final double GUARD_SLOW_LOCK_SHRINK = 4.0D;
    public static final double GUARD_FAST_SPEED_SQR = 3.15D;
    public static final double GUARD_VEHICLE_SPEED_FACTOR = 34.0D;
    public static final float GUARD_LOCK_STAMINA = 10.0F;
    public static final float GUARD_HOLD_STAMINA = 9.5F;
    public static final float GUARD_DEFLECT_STAMINA = 0.5F;

    private KraftWorkSpec() {}
}
