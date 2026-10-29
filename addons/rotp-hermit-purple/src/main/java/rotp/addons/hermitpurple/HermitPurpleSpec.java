package rotp.addons.hermitpurple;

public final class HermitPurpleSpec {
    public static final double POWER = 6.0D;
    public static final double SPEED = 10.0D;
    public static final double EFFECTIVE_RANGE = 3.0D;
    public static final double MAX_RANGE = 3.0D;
    public static final double DURABILITY = 14.0D;
    public static final double PRECISION = 7.0D;
    public static final double LEGACY_RANDOM_WEIGHT = 2.0D;

    public static final float VINE_ATTACK_STAMINA = 40.0F;
    // 1.16 HPVineAttack.getStandWindupTicks: max(standWindupDuration 5 -
    // standPerformDuration 10 / 2, 3)
    public static final int VINE_ATTACK_WINDUP_TICKS = 3;
    public static final int VINE_ATTACK_PERFORM_TICKS = 10;
    public static final int VINE_ATTACK_COOLDOWN_TECHNICAL = 10;
    public static final int VINE_ATTACK_COOLDOWN_ADDITIONAL = 5;
    public static final int VINE_ATTACK_ACTIVE_CAP = 2;
    public static final int VINE_ATTACK_LIFETIME_TICKS = 10;
    public static final int VINE_BIND_EXTRA_LIFETIME_TICKS = 5;
    public static final double VINE_ATTACK_RANGE = 32.0D;
    public static final float VINE_ATTACK_DAMAGE = 6.0F;
    public static final float VINE_BIND_DAMAGE = 2.0F;
    public static final float VINE_ATTACK_VELOCITY = 0.8F;
    public static final float VINE_ATTACK_KNOCKBACK = 0.15F;
    public static final float VINE_HAMON_DAMAGE = 0.4F;
    public static final float VINE_HAMON_POINTS_COST = 200.0F;
    public static final float VINE_FINISHER_GAIN = 0.085F;
    public static final int VINE_FINISHER_NO_DECAY_TICKS = 40;
    public static final int VINE_BIND_STUN_REFRESH_TICKS = 3;

    public static final float GRAB_STAMINA = 40.0F;
    public static final float GRAB_FOLLOWUP_STAMINA = 200.0F;
    public static final float GRAPPLE_STAMINA_PER_TICK = 1.0F;
    // 1.16 standWindupDuration of the hold-only hp_grab and hp_vine: only the
    // length of the raise animation, never a gameplay phase.
    public static final int GRAB_WINDUP_TICKS = 5;
    // 1.16 HPVineGrabEntity: set when the grab vine is removed.
    public static final int GRAB_COOLDOWN_TICKS = 150;
    public static final int GRAB_COOLDOWN_RESOLVE_TICKS = 75;
    public static final int GRAPPLE_WINDUP_TICKS = 10;
    // 1.16 HPGrapplingVineEntity: 4 blocks a tick for 20 ticks, no limit
    // once attached; the hold lasts until release, an empty bar included.
    public static final int GRAPPLE_FLIGHT_TICKS = 20;
    public static final double GRAPPLE_VINE_SPEED = 4.0D;
    // 1.16 HPVineGrabEntity: 2 blocks a tick for 7 ticks, 160 once attached.
    public static final int GRAB_FLIGHT_TICKS = 7;
    public static final int GRAB_ATTACHED_TICKS = 160;
    public static final double GRAB_VINE_SPEED = 2.0D;
    public static final double GRAB_RELEASE_DISTANCE = 10.0D;
    public static final double GRAB_PULL_STOP_DISTANCE = 4.0D;
    public static final double GRAB_PULL_SPEED = 0.75D;
    public static final int GRAB_CLOSE_RELEASE_TICKS = 100;
    public static final int HELD_VINE_ACTIVE_CAP = 1;
    // Longest held vine (the grapple), also the render clamp.
    public static final double HELD_VINE_RANGE =
            GRAPPLE_VINE_SPEED * GRAPPLE_FLIGHT_TICKS;
    public static final int BIND_EFFECT_REFRESH_TICKS = 3;
    public static final double BIND_PULL_STOP_DISTANCE = 2.0D;
    public static final double BIND_PULL_SPEED = 1.0D;
    public static final double GRAPPLE_PULL_SPEED = 2.0D;
    public static final float GRAB_OVERDRIVE_DAMAGE = 5.0F;
    public static final float GRAB_OVERDRIVE_ENERGY_RATIO = 0.2F;
    public static final float GRAB_FOLLOWUP_POINTS = 5.0F;
    public static final float HAMON_BEAT_ENERGY = 1500.0F;
    public static final float HAMON_BEAT_DAMAGE = 3.0F;

    public static final float THORNS_STAMINA_PER_TICK = 1.0F;
    public static final float THORNS_FLAT_REDUCTION =
            (float) (3.0D * DURABILITY / 8.0D);
    public static final float THORNS_REFLECT_DAMAGE = 1.0F;
    public static final float THORNS_HAMON_REFLECT_DAMAGE = 1.0F;

    public static final float BARRIER_STAMINA = 50.0F;
    public static final int BARRIER_MAX_PLACED = 15;
    public static final double BARRIER_RANGE = 50.0D;
    public static final float BARRIER_DAMAGE = 2.0F;
    public static final float BARRIER_HAMON_DAMAGE = 1.0F;
    public static final int BARRIER_RIPPED_TICKS = 40;

    public static final float CRINGE_STAMINA = 50.0F;
    public static final int CRINGE_COOLDOWN_TICKS = 300;
    public static final float DOXX_STAMINA = 30.0F;
    public static final int DOXX_WINDUP_TICKS = 10;
    public static final int DOXX_PERFORM_TICKS = 10;
    public static final int DOXX_COOLDOWN_TICKS = 200;
    public static final int DOXX_RANDOM_RANGE = 1000;
    public static final int DOXX_STRUCTURE_RANGE = 100;
    public static final int DOXX_BIOME_RANGE = 3000;
    public static final int MAP_COLOR = 15_757_520;

    public static final float CAMERA_STAMINA = 20.0F;
    public static final int CAMERA_WINDUP_TICKS = 10;
    public static final int CAMERA_RECOVERY_TICKS = 10;
    public static final int CAMERA_COOLDOWN_TICKS = 100;
    public static final int TARGET_MODE_STAND = -1;
    public static final int TARGET_MODE_RANDOM = 0;
    public static final int TARGET_MODE_PLAYER = 1;
    public static final int TARGET_MODE_ENTITY = 2;
    public static final int TARGET_MODE_STRUCTURE = 3;
    public static final int TARGET_MODE_BIOME = 4;

    public static final float LEAP_STRENGTH_JUMP = 1.25F;
    public static final float LEAP_STRENGTH_AFTERIMAGES = 2.0F;

    private HermitPurpleSpec() {}
}
