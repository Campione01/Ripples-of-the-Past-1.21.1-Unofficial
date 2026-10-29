package rotp.addons.yellowtemperance;

import java.util.List;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class YellowTemperanceConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue EROSION_TRIGGER_TICKS;
    public static final ModConfigSpec.IntValue EROSION_RESET_DURATION;
    public static final ModConfigSpec.IntValue
            EROSION_FIRE_RESET_DURATION;
    public static final ModConfigSpec.IntValue EROSION_MAX_LEVEL;
    public static final ModConfigSpec.DoubleValue EROSION_BASE_DAMAGE;
    public static final ModConfigSpec.DoubleValue
            EROSION_DAMAGE_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue EROSION_MAX_DAMAGE;
    public static final ModConfigSpec.DoubleValue
            PROTECTION_REDUCTION_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue
            PROTECTION_MAX_REDUCTION;

    public static final ModConfigSpec.BooleanValue
            ENABLE_FOOD_BAR_OVERLAY;
    public static final ModConfigSpec.BooleanValue
            ENABLE_APPLE_SKIN_INTEGRATION;

    public static final ModConfigSpec.IntValue
            ATTACK_MAX_EROSION_LEVEL;
    public static final ModConfigSpec.IntValue
            ATTACK_EROSION_INCREASE;
    public static final ModConfigSpec.IntValue
            BLOCK_PROTECTION_LEVEL;

    public static final ModConfigSpec.DoubleValue MIN_ENTITY_HEIGHT;
    public static final ModConfigSpec.DoubleValue MAX_ENTITY_HEIGHT;
    public static final ModConfigSpec.DoubleValue MIN_ENTITY_WIDTH;
    public static final ModConfigSpec.DoubleValue MAX_ENTITY_WIDTH;
    public static final ModConfigSpec.ConfigValue<List<? extends String>>
            ENTITY_DISGUISE_BLACKLIST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>>
            STAND_DISGUISE_BLACKLIST;
    public static final ModConfigSpec.DoubleValue
            DISGUISE_STAND_TRANSITION_TICKS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>>
            STAND_PORTRAIT_NAMESPACES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("erosion");
        EROSION_TRIGGER_TICKS = builder.defineInRange(
                "triggerTicks", 10, 1, 1000);
        EROSION_RESET_DURATION = builder.defineInRange(
                "resetDuration", 210, 1, 10000);
        EROSION_FIRE_RESET_DURATION = builder.defineInRange(
                "fireResetDuration", 110, 1, 10000);
        EROSION_MAX_LEVEL = builder.defineInRange(
                "maxLevel", 255, 1, 255);
        EROSION_BASE_DAMAGE = builder.defineInRange(
                "baseDamage", 0.5D, 0.1D, 100.0D);
        EROSION_DAMAGE_PER_LEVEL = builder.defineInRange(
                "damagePerLevel", 0.5D, 0.1D, 50.0D);
        EROSION_MAX_DAMAGE = builder.defineInRange(
                "maxDamage", 20.0D, 1.0D, 1000.0D);
        ATTACK_MAX_EROSION_LEVEL = builder.defineInRange(
                "attackMaxLevel", 5, 1, 255);
        ATTACK_EROSION_INCREASE = builder.defineInRange(
                "attackLevelIncrease", 1, 1, 10);
        builder.pop();

        builder.push("protection");
        PROTECTION_REDUCTION_PER_LEVEL = builder.defineInRange(
                "reductionPerLevel", 0.3D, 0.01D, 1.0D);
        PROTECTION_MAX_REDUCTION = builder.defineInRange(
                "maximumReduction", 0.98D, 0.1D, 1.0D);
        BLOCK_PROTECTION_LEVEL = builder.defineInRange(
                "blockLevel", 4, 0, 255);
        builder.pop();

        builder.push("client");
        ENABLE_FOOD_BAR_OVERLAY = builder.define(
                "enableFoodBarOverlay", true);
        ENABLE_APPLE_SKIN_INTEGRATION = builder.define(
                "enableAppleSkinIntegration", true);
        STAND_PORTRAIT_NAMESPACES = builder.defineList(
                "standPortraitNamespaces",
                List.of("rotp_extra_dg"),
                value -> value instanceof String);
        builder.pop();

        builder.push("disguise");
        MIN_ENTITY_HEIGHT = builder.defineInRange(
                "minimumEntityHeight", 1.0D, 0.1D, 10.0D);
        MAX_ENTITY_HEIGHT = builder.defineInRange(
                "maximumEntityHeight", 3.0D, 0.5D, 20.0D);
        MIN_ENTITY_WIDTH = builder.defineInRange(
                "minimumEntityWidth", 0.5D, 0.1D, 5.0D);
        MAX_ENTITY_WIDTH = builder.defineInRange(
                "maximumEntityWidth", 2.0D, 0.3D, 10.0D);
        ENTITY_DISGUISE_BLACKLIST = builder.defineList(
                "entityBlacklist",
                List.of(
                        "minecraft:fireball",
                        "minecraft:small_fireball",
                        "minecraft:dragon_fireball",
                        "minecraft:wither_skull",
                        "minecraft:shulker_bullet",
                        "minecraft:arrow",
                        "minecraft:spectral_arrow",
                        "minecraft:trident",
                        "minecraft:snowball",
                        "minecraft:egg",
                        "minecraft:ender_pearl",
                        "minecraft:experience_bottle",
                        "minecraft:potion",
                        "minecraft:llama_spit",
                        "minecraft:end_crystal",
                        "minecraft:boat",
                        "minecraft:minecart"),
                () -> "",
                value -> value instanceof String);
        STAND_DISGUISE_BLACKLIST = builder.defineList(
                "standBlacklist",
                List.of(),
                () -> "",
                value -> value instanceof String);
        DISGUISE_STAND_TRANSITION_TICKS = builder.defineInRange(
                "standTransitionTicks", 10.0D, 1.0D, 200.0D);
        builder.pop();

        SPEC = builder.build();
    }

    private YellowTemperanceConfig() {}
}
