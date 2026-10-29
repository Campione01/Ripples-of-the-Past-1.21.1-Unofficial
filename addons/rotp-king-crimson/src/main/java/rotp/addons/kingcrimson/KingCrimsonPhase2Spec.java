package rotp.addons.kingcrimson;

public final class KingCrimsonPhase2Spec {
    public static final float CHOP_DAMAGE_MULTIPLIER = 1.15F;
    public static final float IMPALE_DAMAGE_MULTIPLIER = 0.9F;
    public static final float ARMOR_PIERCING_PER_STRENGTH = 0.01F;

    public static final float CHOP_STAMINA = 200.0F;
    public static final float IMPALE_STAMINA = 200.0F;
    public static final float PROJECTILE_STAMINA = 120.0F;

    public static final int CHOP_COOLDOWN_TICKS = 350;
    public static final int IMPALE_COOLDOWN_TICKS = 180;
    public static final int PROJECTILE_COOLDOWN_TICKS = 95;

    public static final int CHOP_EFFECT_TICKS = 80;
    public static final int IMPALE_EFFECT_TICKS = 140;
    public static final int SHIELD_COOLDOWN_TICKS = 100;
    // 1.16 KCConfig defaults (impaleGapingWoundLevel, allowedProjectiles with the core's port namespace)
    public static final int IMPALE_GAPING_WOUND_AMPLIFIER = 0;
    public static final String ALLOWED_PROJECTILES =
            "arrow,egg,snowball,ender_pearl,firework_rocket,splash_potion,spectral_arrow,"
                    + "experience_bottle,fire_charge,jojo_ripples:knife,jojo_ripples:molotov";

    public static final float PROJECTILE_SPEED = 3.0F;
    public static final float PROJECTILE_INACCURACY = 1.0F;
    public static final double TRIPLE_PROJECTILE_SPREAD = 0.2D;

    private KingCrimsonPhase2Spec() {}
}
