package rotp.addons.cream;

import java.util.List;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class CreamConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue CHOP_COOLDOWN;
    public static final ModConfigSpec.IntValue CHOMP_COOLDOWN;
    public static final ModConfigSpec.IntValue EAT_ITEM_COOLDOWN;
    public static final ModConfigSpec.IntValue EAT_ITEM_STACK_COOLDOWN;

    public static final ModConfigSpec.BooleanValue VOID_INSTAKILLS;
    public static final ModConfigSpec.IntValue VOID_DAMAGE;
    public static final ModConfigSpec.IntValue VOID_DAMAGE_COOLDOWN;
    public static final ModConfigSpec.BooleanValue
            EXCLUDE_VOID_FROM_BLOCK_BREAKING;
    public static final ModConfigSpec.BooleanValue CONVERT_GRASS_TO_DIRT;
    public static final ModConfigSpec.ConfigValue<List<? extends String>>
            VOID_BLOCK_BLACKLIST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>>
            VOID_ENTITY_BLACKLIST;

    public static final ModConfigSpec.DoubleValue VOID_BALL_STAMINA;
    public static final ModConfigSpec.BooleanValue
            VOID_BALL_CANCEL_ON_DAMAGE;
    public static final ModConfigSpec.BooleanValue
            VOID_BALL_DELETE_ITEMS;
    public static final ModConfigSpec.DoubleValue
            VOID_BALL_STAMINA_PER_TICK;
    public static final ModConfigSpec.IntValue VOID_BALL_DURATION;
    public static final ModConfigSpec.IntValue VOID_BALL_COOLDOWN;
    public static final ModConfigSpec.BooleanValue
            VOID_BALL_DYNAMIC_COOLDOWN;
    public static final ModConfigSpec.DoubleValue
            VOID_BALL_DYNAMIC_MULTIPLIER;
    public static final ModConfigSpec.IntValue
            VOID_BALL_DYNAMIC_MINIMUM;

    public static final ModConfigSpec.BooleanValue HAVE_VOID_DASH;
    public static final ModConfigSpec.DoubleValue VOID_DASH_STAMINA;
    public static final ModConfigSpec.BooleanValue
            VOID_DASH_DELETE_ITEMS;
    public static final ModConfigSpec.IntValue VOID_DASH_COOLDOWN;

    public static final ModConfigSpec.DoubleValue SEMI_VOID_STAMINA;
    public static final ModConfigSpec.BooleanValue
            SEMI_VOID_CANCEL_ON_DAMAGE;
    public static final ModConfigSpec.DoubleValue
            SEMI_VOID_STAMINA_PER_TICK;
    public static final ModConfigSpec.IntValue SEMI_VOID_DURATION;
    public static final ModConfigSpec.IntValue SEMI_VOID_COOLDOWN;
    public static final ModConfigSpec.BooleanValue
            SEMI_VOID_DYNAMIC_COOLDOWN;
    public static final ModConfigSpec.DoubleValue
            SEMI_VOID_DYNAMIC_MULTIPLIER;
    public static final ModConfigSpec.IntValue
            SEMI_VOID_DYNAMIC_MINIMUM;
    public static final ModConfigSpec.DoubleValue SEMI_VOID_FLY_SPEED;
    public static final ModConfigSpec.DoubleValue
            SEMI_VOID_SPRINT_FLY_SPEED;

    public static final ModConfigSpec.BooleanValue
            PROGRESSIVE_STAMINA_COST;
    public static final ModConfigSpec.DoubleValue
            STAMINA_COST_RESOLVE_2;
    public static final ModConfigSpec.DoubleValue
            STAMINA_COST_RESOLVE_3;
    public static final ModConfigSpec.DoubleValue
            STAMINA_COST_RESOLVE_4;
    public static final ModConfigSpec.DoubleValue
            STAMINA_COST_RESOLVE_2_VAMPIRE;
    public static final ModConfigSpec.DoubleValue
            STAMINA_COST_RESOLVE_3_VAMPIRE;
    public static final ModConfigSpec.DoubleValue
            STAMINA_COST_RESOLVE_4_VAMPIRE;
    public static final ModConfigSpec.DoubleValue
            VOID_BALL_STAMINA_PER_TICK_VAMPIRE;
    public static final ModConfigSpec.DoubleValue
            SEMI_VOID_STAMINA_PER_TICK_VAMPIRE;
    public static final ModConfigSpec.IntValue
            VOID_BALL_VAMPIRE_COOLDOWN;
    public static final ModConfigSpec.DoubleValue
            VOID_BALL_VAMPIRE_COOLDOWN_MULTIPLIER;
    public static final ModConfigSpec.IntValue
            VOID_BALL_VAMPIRE_MINIMUM;
    public static final ModConfigSpec.IntValue
            SEMI_VOID_VAMPIRE_COOLDOWN;
    public static final ModConfigSpec.DoubleValue
            SEMI_VOID_VAMPIRE_COOLDOWN_MULTIPLIER;
    public static final ModConfigSpec.IntValue
            SEMI_VOID_VAMPIRE_MINIMUM;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("combat");
        CHOP_COOLDOWN = intValue(
                builder, "chopCooldown", 175, 0, 120000);
        CHOMP_COOLDOWN = intValue(
                builder, "chompCooldown", 360, 0, 120000);
        EAT_ITEM_COOLDOWN = intValue(
                builder, "eatItemCooldown", 20, 0, 120000);
        EAT_ITEM_STACK_COOLDOWN = intValue(
                builder, "eatItemStackCooldown", 80, 0, 120000);
        builder.pop();

        builder.push("void");
        VOID_INSTAKILLS = builder.define("voidInstaKills", true);
        VOID_DAMAGE = intValue(
                builder, "voidDamage", 30, 0, 120000);
        VOID_DAMAGE_COOLDOWN = intValue(
                builder, "voidDamageCooldown", 20, 0, 120000);
        EXCLUDE_VOID_FROM_BLOCK_BREAKING = builder.define(
                "excludeVoidFromBlockBreaking", false);
        CONVERT_GRASS_TO_DIRT = builder.define(
                "convertGrassToDirt", true);
        VOID_BLOCK_BLACKLIST = builder.defineList(
                "blockBlacklist",
                List.of(
                        "minecraft:bedrock",
                        "minecraft:barrier",
                        "minecraft:command_block",
                        "minecraft:repeating_command_block",
                        "minecraft:chain_command_block",
                        "minecraft:structure_block",
                        "minecraft:structure_void",
                        "minecraft:end_portal",
                        "minecraft:end_portal_frame",
                        "minecraft:nether_portal",
                        "minecraft:end_gateway",
                        "gravestone:gravestone"),
                () -> "",
                value -> value instanceof String);
        VOID_ENTITY_BLACKLIST = builder.defineList(
                "entityBlacklist",
                List.of(
                        "minecraft:ender_dragon",
                        "minecraft:wither",
                        "corpse:corpse",
                        "enigmaticgraves:grave"),
                () -> "",
                value -> value instanceof String);
        builder.pop();

        builder.push("voidBall");
        VOID_BALL_STAMINA = doubleValue(
                builder, "activationStamina", 360.0D, 0.0D, 10000.0D);
        VOID_BALL_CANCEL_ON_DAMAGE = builder.define(
                "cancelOnDamageDuringCharge", true);
        VOID_BALL_DELETE_ITEMS = builder.define(
                "deleteItems", false);
        VOID_BALL_STAMINA_PER_TICK = doubleValue(
                builder, "staminaPerTick", 1.0D, 0.0D, 100.0D);
        VOID_BALL_DURATION = intValue(
                builder,
                "duration",
                Integer.MAX_VALUE,
                0,
                Integer.MAX_VALUE);
        VOID_BALL_COOLDOWN = intValue(
                builder, "cooldown", 240, 0, 120000);
        VOID_BALL_DYNAMIC_COOLDOWN = builder.define(
                "dynamicCooldown", true);
        VOID_BALL_DYNAMIC_MULTIPLIER = doubleValue(
                builder, "dynamicMultiplier", 2.0D, 0.0D, 10.0D);
        VOID_BALL_DYNAMIC_MINIMUM = intValue(
                builder, "dynamicMinimum", 120, 0, 120000);
        builder.pop();

        builder.push("voidDash");
        HAVE_VOID_DASH = builder.define("enabled", true);
        VOID_DASH_STAMINA = doubleValue(
                builder, "activationStamina", 300.0D, 0.0D, 10000.0D);
        VOID_DASH_DELETE_ITEMS = builder.define("deleteItems", false);
        VOID_DASH_COOLDOWN = intValue(
                builder, "cooldown", 240, 0, 120000);
        builder.pop();

        builder.push("semiVoid");
        SEMI_VOID_STAMINA = doubleValue(
                builder, "activationStamina", 300.0D, 0.0D, 10000.0D);
        SEMI_VOID_CANCEL_ON_DAMAGE = builder.define(
                "cancelOnDamageDuringCharge", true);
        SEMI_VOID_STAMINA_PER_TICK = doubleValue(
                builder, "staminaPerTick", 2.0D, 0.0D, 100.0D);
        SEMI_VOID_DURATION = intValue(
                builder,
                "duration",
                Integer.MAX_VALUE,
                0,
                Integer.MAX_VALUE);
        SEMI_VOID_COOLDOWN = intValue(
                builder, "cooldown", 240, 0, 120000);
        SEMI_VOID_DYNAMIC_COOLDOWN = builder.define(
                "dynamicCooldown", true);
        SEMI_VOID_DYNAMIC_MULTIPLIER = doubleValue(
                builder, "dynamicMultiplier", 1.5D, 0.0D, 10.0D);
        SEMI_VOID_DYNAMIC_MINIMUM = intValue(
                builder, "dynamicMinimum", 120, 0, 120000);
        SEMI_VOID_FLY_SPEED = doubleValue(
                builder, "flySpeed", 0.04D, 0.0D, 1.0D);
        SEMI_VOID_SPRINT_FLY_SPEED = doubleValue(
                builder, "sprintFlySpeed", 0.02D, 0.0D, 1.0D);
        builder.pop();

        builder.push("progression");
        PROGRESSIVE_STAMINA_COST = builder.define(
                "progressiveStaminaCost", true);
        STAMINA_COST_RESOLVE_2 = doubleValue(
                builder, "resolve2", 7.0D, 0.0D, 12000.0D);
        STAMINA_COST_RESOLVE_3 = doubleValue(
                builder, "resolve3", 5.0D, 0.0D, 12000.0D);
        STAMINA_COST_RESOLVE_4 = doubleValue(
                builder, "resolve4", 3.0D, 0.0D, 12000.0D);
        STAMINA_COST_RESOLVE_2_VAMPIRE = doubleValue(
                builder, "resolve2Vampire", 6.0D, 0.0D, 12000.0D);
        STAMINA_COST_RESOLVE_3_VAMPIRE = doubleValue(
                builder, "resolve3Vampire", 4.0D, 0.0D, 12000.0D);
        STAMINA_COST_RESOLVE_4_VAMPIRE = doubleValue(
                builder, "resolve4Vampire", 2.0D, 0.0D, 12000.0D);
        VOID_BALL_STAMINA_PER_TICK_VAMPIRE = doubleValue(
                builder,
                "voidBallStaminaPerTickVampire",
                1.0D,
                0.0D,
                100.0D);
        SEMI_VOID_STAMINA_PER_TICK_VAMPIRE = doubleValue(
                builder,
                "semiVoidStaminaPerTickVampire",
                1.5D,
                0.0D,
                100.0D);
        VOID_BALL_VAMPIRE_COOLDOWN = intValue(
                builder, "voidBallVampireCooldown", 200, 0, 120000);
        VOID_BALL_VAMPIRE_COOLDOWN_MULTIPLIER = doubleValue(
                builder,
                "voidBallVampireCooldownMultiplier",
                1.5D,
                0.0D,
                10.0D);
        VOID_BALL_VAMPIRE_MINIMUM = intValue(
                builder, "voidBallVampireMinimum", 100, 0, 120000);
        SEMI_VOID_VAMPIRE_COOLDOWN = intValue(
                builder, "semiVoidVampireCooldown", 200, 0, 120000);
        SEMI_VOID_VAMPIRE_COOLDOWN_MULTIPLIER = doubleValue(
                builder,
                "semiVoidVampireCooldownMultiplier",
                1.25D,
                0.0D,
                10.0D);
        SEMI_VOID_VAMPIRE_MINIMUM = intValue(
                builder, "semiVoidVampireMinimum", 100, 0, 120000);
        builder.pop();

        SPEC = builder.build();
    }

    private CreamConfig() {}

    private static ModConfigSpec.IntValue intValue(
            ModConfigSpec.Builder builder,
            String name,
            int value,
            int min,
            int max) {
        return builder.defineInRange(name, value, min, max);
    }

    private static ModConfigSpec.DoubleValue doubleValue(
            ModConfigSpec.Builder builder,
            String name,
            double value,
            double min,
            double max) {
        return builder.defineInRange(name, value, min, max);
    }
}
