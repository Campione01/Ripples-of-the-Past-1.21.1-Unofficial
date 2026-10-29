package rotp.addons.kingcrimson;

public final class KingCrimsonPhase4Spec {
    public static final float GROUND_PUNCH_DAMAGE_MULTIPLIER = 1.0F;
    public static final double GROUND_PUNCH_RANGE = 3.0D;
    public static final float GROUND_PUNCH_KNOCKBACK = 1.5F;
    public static final double GROUND_PUNCH_VERTICAL_KNOCKBACK = 0.4D;
    // 1.16 StandEntityHeavyAttack.DEFAULT_STAMINA_COST
    public static final float GROUND_PUNCH_STAMINA = 50.0F;

    public static final float GROUND_SLAM_DAMAGE_MULTIPLIER = 1.075F;
    public static final double GROUND_SLAM_RANGE = 2.0D;
    public static final float GROUND_SLAM_KNOCKBACK = 0.75F;
    public static final double GROUND_SLAM_VERTICAL_KNOCKBACK = 0.2D;
    public static final int GROUND_SLAM_WINDUP_TICKS = 5;
    public static final int GROUND_SLAM_RECOVERY_TICKS = 4;
    // 1.16 StandEntityLightAttack.Builder default staminaCost(10F)
    public static final float GROUND_SLAM_STAMINA = 10.0F;

    public static final float EYEJAB_HEALTH_THRESHOLD = 0.5F;
    public static final float EYEJAB_DAMAGE_MULTIPLIER = 0.9F;
    public static final float EYEJAB_ARMOR_PIERCING_PER_STRENGTH = 0.01F;
    public static final float EYEJAB_STAMINA = 200.0F;
    public static final int EYEJAB_COOLDOWN_TICKS = 200;
    public static final int EYEJAB_WINDUP_TICKS = 10;
    public static final int HALF_BLINDNESS_TICKS = 60;
    public static final int EYEJAB_SLOWNESS_TICKS = 80;
    public static final int EYEJAB_BLINDNESS_TICKS = 80;
    public static final int EYEJAB_BLINDNESS_AMPLIFIER = 9;

    private KingCrimsonPhase4Spec() {}
}
