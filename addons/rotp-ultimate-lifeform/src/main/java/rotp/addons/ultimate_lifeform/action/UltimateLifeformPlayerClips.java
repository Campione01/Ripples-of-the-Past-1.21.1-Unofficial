package rotp.addons.ultimate_lifeform.action;

import javax.annotation.Nullable;

import rotp.addons.ultimate_lifeform.power.UltimateLifeformSpec;

/**
 * Core player clips the Ultimate moves played in 1.16. The add-on ships
 * only its wall-climb clips, so these point at the core Hamon and
 * Pillar Man animation sets.
 */
public final class UltimateLifeformPlayerClips {
    public static final String CORE_NAMESPACE = "jojo_ripples";
    public static final String PILLAR_MAN = "pillar_man";
    public static final String HAMON = "hamon";

    public record Clip(String animSet, String name) {}

    private static final Clip PILLAR_MAN_PUNCH =
            new Clip(PILLAR_MAN, "pillar_man_punch");
    private static final Clip HAMON_BEAT = new Clip(HAMON, "hamon_beat");
    private static final Clip SENDO_WAVE_KICK =
            new Clip(HAMON, "sendo_wave_kick");
    private static final Clip HAMON_SHOCK = new Clip(HAMON, "hamon_shock");
    private static final Clip HAMON_BREATH = new Clip(HAMON, "hamon_breath");
    private static final Clip PUNCH_BARRAGE =
            new Clip(HAMON, "punch_barrage");
    private static final Clip BLADE_SLASH =
            new Clip(PILLAR_MAN, "blade_slash");
    private static final Clip BLADE_DASH = new Clip(PILLAR_MAN, "blade_dash");
    private static final Clip BLADE_BARRAGE =
            new Clip(PILLAR_MAN, "blade_barrage");
    private static final Clip ERRATIC_BLAZE_KING =
            new Clip(PILLAR_MAN, "erratic_blaze_king");
    private static final Clip UNNATURAL_AGILITY =
            new Clip(PILLAR_MAN, "unnatural_agility");
    private static final Clip EVASION = new Clip(PILLAR_MAN, "evasion");
    private static final Clip LIGHT_FLASH =
            new Clip(PILLAR_MAN, "light_flash");
    private static final Clip LIGHT_FLASH_DECOY =
            new Clip(PILLAR_MAN, "light_flash_decoy");
    private static final Clip GIANT_CARTWHEEL_PRISON =
            new Clip(PILLAR_MAN, "giant_cartwheel_prison");
    private static final Clip SELF_DETONATION =
            new Clip(PILLAR_MAN, "self_detonation");
    private static final Clip DIVINE_SANDSTORM =
            new Clip(PILLAR_MAN, "divine_sandstorm");
    private static final Clip ATMOSPHERIC_RIFT =
            new Clip(PILLAR_MAN, "atmospheric_rift");

    /** Stone form poses, one picked at random per toggle as in 1.16. */
    public static final String[] STONE_FORM_POSES = {
            "stone_form_1", "stone_form_2", "stone_form_3"};

    private UltimateLifeformPlayerClips() {}

    /** Null when the move had no player pose in 1.16. */
    @Nullable
    public static Clip forSpec(UltimateLifeformSpec spec) {
        return switch (spec) {
            case HEAVY_PUNCH, ULTIMATE_HAMON_OVERDRIVE -> PILLAR_MAN_PUNCH;
            case HAMON_OVERDRIVE_BEAT -> HAMON_BEAT;
            case ULTIMATE_HAMON_KICK -> SENDO_WAVE_KICK;
            case HAMON_SHOCK -> HAMON_SHOCK;
            case HAMON_BREATH -> HAMON_BREATH;
            case ULTIMATE_BARRAGE, ULTIMATE_HAMON_BARRAGE -> PUNCH_BARRAGE;
            case BLADE_SLASH -> BLADE_SLASH;
            case BLADE_DASH_ATTACK -> BLADE_DASH;
            case BLADE_BARRAGE -> BLADE_BARRAGE;
            case ERRATIC_BLAZE_KING -> ERRATIC_BLAZE_KING;
            case ULTIMATE_AGILITY -> UNNATURAL_AGILITY;
            // inherited from the core Pillar Man moves in 1.16
            case EVASION -> EVASION;
            case LIGHT_FLASH -> LIGHT_FLASH;
            case LIGHT_FLASH_DECOY -> LIGHT_FLASH_DECOY;
            case GIANT_CARTHWHEEL_PRISON -> GIANT_CARTWHEEL_PRISON;
            case SELF_DETONATION -> SELF_DETONATION;
            case DIVINE_SANDSTORM -> DIVINE_SANDSTORM;
            case ATMOSPHERIC_RIFT -> ATMOSPHERIC_RIFT;
            default -> null;
        };
    }

    /** Sendo wave kick pose by which hands hold an item (1.16 handler). */
    public static String sendoWaveKick(
            boolean leftHandItem, boolean rightHandItem) {
        if (leftHandItem) {
            return rightHandItem
                    ? "sendo_wave_kick_lr"
                    : "sendo_wave_kick_l";
        }
        return rightHandItem
                ? "sendo_wave_kick_r"
                : SENDO_WAVE_KICK.name();
    }
}
