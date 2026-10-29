package rotp.addons.sexpistols.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class SexPistolsConfig {
    public static final double DEFAULT_BULLET_DAMAGE_MULTIPLIER = 1.0D;
    public static final boolean DEFAULT_BULLET_DESTROYS_BLOCKS = true;
    public static final int DEFAULT_BLOCK_DAMAGE_MEMORY_TICKS = 100;
    public static final double DEFAULT_STABLE_DAMAGE_END_DISTANCE = 50.0D;
    public static final double DEFAULT_CLOSE_DAMAGE_DROP_DISTANCE = 5.0D;
    public static final double DEFAULT_MIN_DISTANCE_DAMAGE = 1.0D;
    public static final int DEFAULT_MAX_RICOCHET_COUNT = 2;
    public static final double DEFAULT_TARGET_SEARCH_RANGE = 32.0D;
    public static final double DEFAULT_TRANSFER_ASSIST_RANGE = 18.0D;
    public static final int DEFAULT_SEX_PISTOLS_REVIVE_TICKS = 1_200;
    public static final boolean DEFAULT_SEX_PISTOLS_HUNGER_ENABLED = true;
    public static final int DEFAULT_SEX_PISTOLS_HUNGER_MIN_TICKS = 6_000;
    public static final int DEFAULT_SEX_PISTOLS_HUNGER_EXTRA_TICKS = 6_000;
    public static final double MAX_BULLET_DAMAGE_MULTIPLIER = 100.0D;
    public static final double MAX_STABLE_DAMAGE_END_DISTANCE = 4_096.0D;
    public static final double MAX_CLOSE_DAMAGE_DROP_DISTANCE = 4_096.0D;
    public static final double MAX_MIN_DISTANCE_DAMAGE = 1_000.0D;
    public static final double MAX_TARGET_SEARCH_RANGE = 128.0D;
    public static final double MAX_TRANSFER_ASSIST_RANGE = 128.0D;

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue BULLET_DAMAGE_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue IS_BULLET_DESTROYED_BLOCKS;
    public static final ModConfigSpec.IntValue BLOCK_DAMAGE_MEMORY_TICKS;
    public static final ModConfigSpec.DoubleValue STABLE_DAMAGE_END_DISTANCE;
    public static final ModConfigSpec.DoubleValue CLOSE_DAMAGE_DROP_DISTANCE;
    public static final ModConfigSpec.DoubleValue MIN_DISTANCE_DAMAGE;
    public static final ModConfigSpec.IntValue MAX_RICOCHET_COUNT;
    public static final ModConfigSpec.DoubleValue SEX_PISTOLS_TARGET_SEARCH_RANGE;
    public static final ModConfigSpec.DoubleValue SEX_PISTOLS_TRANSFER_ASSIST_RANGE;
    public static final ModConfigSpec.IntValue SEX_PISTOLS_REVIVE_TICKS;
    public static final ModConfigSpec.BooleanValue IS_SEX_PISTOLS_HUNGER;
    public static final ModConfigSpec.IntValue SEX_PISTOLS_HUNGER_MIN_TICKS;
    public static final ModConfigSpec.IntValue SEX_PISTOLS_HUNGER_EXTRA_TICKS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Settings for Sex Pistols' Revolver bullets.")
                .push("Revolver Settings");
        BULLET_DAMAGE_MULTIPLIER = builder
                .comment(
                        "Global damage multiplier applied to all Revolver bullet attacks.",
                        "Affects normal shots, Piercing Shot, Splitting Shot, and Encirclement.",
                        "0.0 = no damage, 1.0 = default, 2.0 = double damage.")
                .translation("rotp_sex_pistols.config.bulletDamageMultiplier")
                .defineInRange(
                        "bulletDamageMultiplier",
                        DEFAULT_BULLET_DAMAGE_MULTIPLIER,
                        0.0D,
                        MAX_BULLET_DAMAGE_MULTIPLIER);
        IS_BULLET_DESTROYED_BLOCKS = builder
                .comment(
                        "Can bullet attacks destroy blocks.",
                        "Affects normal shots and Piercing Shot.",
                        "Defaults to true.")
                .translation("rotp_sex_pistols.config.IsBulletDestroyedBlocks")
                .define(
                        "IsBulletDestroyedBlocks",
                        DEFAULT_BULLET_DESTROYS_BLOCKS);
        BLOCK_DAMAGE_MEMORY_TICKS = builder
                .comment(
                        "Duration of damage inflicted by a bullet on a block, in ticks.",
                        "Defaults to 100.")
                .translation("rotp_sex_pistols.config.blockDamageMemoryTicks")
                .defineInRange(
                        "blockDamageMemoryTicks",
                        DEFAULT_BLOCK_DAMAGE_MEMORY_TICKS,
                        0,
                        Integer.MAX_VALUE);
        STABLE_DAMAGE_END_DISTANCE = builder
                .comment(
                        "Maximum distance at which bullets maintain stable damage.",
                        "Damage decreases beyond this distance.",
                        "Defaults to 50.0.")
                .translation("rotp_sex_pistols.config.stableDamageEndDistance")
                .defineInRange(
                        "stableDamageEndDistance",
                        DEFAULT_STABLE_DAMAGE_END_DISTANCE,
                        0.0D,
                        MAX_STABLE_DAMAGE_END_DISTANCE);
        CLOSE_DAMAGE_DROP_DISTANCE = builder
                .comment(
                        "Maximum distance at which bullets can deal extra damage.",
                        "Within this range, bullet damage starts at double damage.",
                        "Defaults to 5.0.")
                .translation("rotp_sex_pistols.config.closeDamageDropDistance")
                .defineInRange(
                        "closeDamageDropDistance",
                        DEFAULT_CLOSE_DAMAGE_DROP_DISTANCE,
                        0.0D,
                        MAX_CLOSE_DAMAGE_DROP_DISTANCE);
        MIN_DISTANCE_DAMAGE = builder
                .comment(
                        "Minimum damage after a bullet travels a long distance.",
                        "Defaults to 1.0.")
                .translation("rotp_sex_pistols.config.minDistanceDamage")
                .defineInRange(
                        "minDistanceDamage",
                        DEFAULT_MIN_DISTANCE_DAMAGE,
                        0.0D,
                        MAX_MIN_DISTANCE_DAMAGE);
        MAX_RICOCHET_COUNT = builder
                .comment(
                        "Maximum number of times a bullet can ricochet.",
                        "Defaults to 2.")
                .translation("rotp_sex_pistols.config.maxRicochetCount")
                .defineInRange(
                        "maxRicochetCount",
                        DEFAULT_MAX_RICOCHET_COUNT,
                        0,
                        Integer.MAX_VALUE);
        builder.pop();

        builder.comment("Stand Settings")
                .push("General Settings");
        builder.comment("Sex Pistols lifecycle, targeting, and relay settings.")
                .push("Sex Pistols Settings");
        SEX_PISTOLS_TARGET_SEARCH_RANGE = builder
                .comment(
                        "Maximum target search range for bullet redirects.",
                        "Defaults to 32.0.")
                .translation("rotp_sex_pistols.config.sexPistolsTargetSearchRange")
                .defineInRange(
                        "sexPistolsTargetSearchRange",
                        DEFAULT_TARGET_SEARCH_RANGE,
                        0.0D,
                        MAX_TARGET_SEARCH_RANGE);
        SEX_PISTOLS_TRANSFER_ASSIST_RANGE = builder
                .comment(
                        "Maximum relay-kick transfer assist range.",
                        "Defaults to 18.0.")
                .translation("rotp_sex_pistols.config.sexPistolsTransferAssistRange")
                .defineInRange(
                        "sexPistolsTransferAssistRange",
                        DEFAULT_TRANSFER_ASSIST_RANGE,
                        0.0D,
                        MAX_TRANSFER_ASSIST_RANGE);
        SEX_PISTOLS_REVIVE_TICKS = builder
                .comment(
                        "Time in ticks required for a defeated Sex Pistols member to revive.",
                        "20 ticks = 1 second. Defaults to 1200 (60 seconds).")
                .translation("rotp_sex_pistols.config.sexPistolsReviveTicks")
                .defineInRange(
                        "sexPistolsReviveTicks",
                        DEFAULT_SEX_PISTOLS_REVIVE_TICKS,
                        1,
                        Integer.MAX_VALUE);
        IS_SEX_PISTOLS_HUNGER = builder
                .comment(
                        "Whether Sex Pistols require feeding over time.",
                        "When hungry, they beg for food and refuse to work until fed.")
                .translation("rotp_sex_pistols.config.isSexPistolsHunger")
                .define(
                        "isSexPistolsHunger",
                        DEFAULT_SEX_PISTOLS_HUNGER_ENABLED);
        SEX_PISTOLS_HUNGER_MIN_TICKS = builder
                .comment(
                        "Minimum time in ticks before Sex Pistols become hungry.",
                        "20 ticks = 1 second. Defaults to 6000 (5 minutes).")
                .translation("rotp_sex_pistols.config.sexPistolsHungerMinTicks")
                .defineInRange(
                        "sexPistolsHungerMinTicks",
                        DEFAULT_SEX_PISTOLS_HUNGER_MIN_TICKS,
                        100,
                        Integer.MAX_VALUE);
        SEX_PISTOLS_HUNGER_EXTRA_TICKS = builder
                .comment(
                        "Additional random ticks added to the hunger timer.",
                        "Total time is minimum plus random(0, extra). Defaults to 6000.")
                .translation("rotp_sex_pistols.config.sexPistolsHungerExtraTicks")
                .defineInRange(
                        "sexPistolsHungerExtraTicks",
                        DEFAULT_SEX_PISTOLS_HUNGER_EXTRA_TICKS,
                        0,
                        Integer.MAX_VALUE);
        builder.pop();
        builder.pop();

        SPEC = builder.build();
    }

    private SexPistolsConfig() {}

    public static double bulletDamageMultiplier() {
        return BULLET_DAMAGE_MULTIPLIER.getAsDouble();
    }

    public static boolean bulletDestroysBlocks() {
        return IS_BULLET_DESTROYED_BLOCKS.get();
    }

    public static long blockDamageMemoryTicks() {
        return BLOCK_DAMAGE_MEMORY_TICKS.getAsInt();
    }

    public static double stableDamageEndDistance() {
        return STABLE_DAMAGE_END_DISTANCE.getAsDouble();
    }

    public static double closeDamageDropDistance() {
        return CLOSE_DAMAGE_DROP_DISTANCE.getAsDouble();
    }

    public static double minDistanceDamage() {
        return MIN_DISTANCE_DAMAGE.getAsDouble();
    }

    public static int maxRicochetCount() {
        return MAX_RICOCHET_COUNT.getAsInt();
    }

    public static double targetSearchRange() {
        return SEX_PISTOLS_TARGET_SEARCH_RANGE.getAsDouble();
    }

    public static double transferAssistRange() {
        return SEX_PISTOLS_TRANSFER_ASSIST_RANGE.getAsDouble();
    }

    public static int reviveTicks() {
        return SEX_PISTOLS_REVIVE_TICKS.getAsInt();
    }

    public static boolean hungerEnabled() {
        return IS_SEX_PISTOLS_HUNGER.get();
    }

    public static int hungerMinTicks() {
        return SEX_PISTOLS_HUNGER_MIN_TICKS.getAsInt();
    }

    public static int hungerExtraTicks() {
        return SEX_PISTOLS_HUNGER_EXTRA_TICKS.getAsInt();
    }
}
