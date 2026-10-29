package rotp.addons.metallica;

public final class MetallicaPhase1Spec {
    public static final double POWER = 9.0D;
    public static final double SPEED = 9.0D;
    public static final double EFFECTIVE_RANGE = 5.0D;
    public static final double MAX_RANGE = 10.0D;
    public static final double DURABILITY = 15.0D;
    public static final double PRECISION = 9.0D;
    public static final double RANDOM_WEIGHT = 1.0D;

    public static final float SINGLE_SCALPEL_STAMINA = 30.0F;
    public static final float MULTIPLE_SCALPEL_STAMINA = 120.0F;
    public static final float INVISIBILITY_STAMINA = 200.0F;
    public static final float INVISIBILITY_STAMINA_PER_TICK = 6.0F;
    public static final float HEAL_STAMINA = 200.0F;
    public static final float VICTIM_BLADES_STAMINA_PER_TICK = 6.5F;

    public static final int SINGLE_SCALPEL_COOLDOWN_TICKS = 5;
    public static final int MULTIPLE_SCALPEL_COOLDOWN_TICKS = 40;
    public static final int HEAL_COOLDOWN_TICKS = 150;
    public static final int VICTIM_BLADES_COOLDOWN_TICKS = 300;
    public static final int INVISIBILITY_CHARGE_TICKS = 30;
    // 1.16 invisibility: standWindupDuration(30) after the released hold.
    public static final int INVISIBILITY_WINDUP_TICKS = 30;
    // 1.16 create_blades_in_victim: holdType(100).
    public static final int VICTIM_BLADES_MAX_HOLD_TICKS = 100;

    public static final int LACK_OF_IRON_DURATION_TICKS = 200;
    public static final int STRIP_INTERVAL_TICKS = 20;

    private MetallicaPhase1Spec() {}
}
