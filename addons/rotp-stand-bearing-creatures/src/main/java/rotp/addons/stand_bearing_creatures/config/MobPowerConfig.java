package rotp.addons.stand_bearing_creatures.config;

import java.util.List;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class MobPowerConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue
            PERCENTAGE_CHANCE_TO_GETTING_A_STAND_FOR_MOB;
    public static final ModConfigSpec.BooleanValue USE_ENTITY_WHITELIST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>>
            ENTITY_LIST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>>
            BLOCKED_STANDS_FOR_MOBS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>>
            BLOCKED_STAND_ACTIONS_FOR_MOBS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>>
            LONG_RANGE_STANDS;
    public static final ModConfigSpec.BooleanValue USE_ADDON_STANDS;
    public static final ModConfigSpec.BooleanValue SMALL_ANARCHY_WITH_STANDS;
    public static final ModConfigSpec.BooleanValue
            DROP_STAND_DISC_FROM_MOBS;
    public static final ModConfigSpec.BooleanValue SPAWN_BOY_2_MAN;
    public static final ModConfigSpec.BooleanValue USE_WIP_COMBO_SYSTEM;
    public static final ModConfigSpec.BooleanValue
            REQUIRE_STAND_ADVANCEMENT_TO_SPAWN_MOBS_WITH_POWERS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("Settings");

        PERCENTAGE_CHANCE_TO_GETTING_A_STAND_FOR_MOB = builder
                .comment("Percentage chance for an eligible new mob to receive a Stand.")
                .defineInRange(
                        "percentageChanceToGettingAStandForMob",
                        5,
                        0,
                        100);
        USE_ENTITY_WHITELIST = builder
                .comment("False treats entityList as a blacklist; true treats it as a whitelist.")
                .define("useEntityWhitelist", false);
        ENTITY_LIST = stringList(
                builder,
                "entityList",
                List.of(
                        "rotp_harvest:harvest",
                        "rotp_zbc:bad_soldier",
                        "rotp_zbc:bad_tank",
                        "rotp_zbc:bad_helicopter",
                        "rotp_pj:pearljam",
                        "rotp_zkq:sheer_heart",
                        "rotp_stfn:player_arm",
                        "rotp_stfn:player_leg",
                        "rotp_stfn:player_head"));
        BLOCKED_STANDS_FOR_MOBS = stringList(
                builder,
                "blockedStandsForMobs",
                List.of(
                        "rotp_harvest:harvest_stand",
                        "rotp_zbc:bad_company",
                        "rotp_wr:weather_report",
                        "rotp_ctr:catch_the_rainbow",
                        "rotp_metallica:metallica",
                        "rotp_zwa:white_album",
                        "rotp_wou:wonder_of_you",
                        "rotp_wonderofu:wonderofu",
                        "rotp_lovers:lovers"));
        BLOCKED_STAND_ACTIONS_FOR_MOBS = stringList(
                builder,
                "blockedStandActions",
                List.of(
                        "rotp_harvest:search",
                        "go_to_this_place",
                        "set_target",
                        "forget_target",
                        "rotp_zbc:set_target",
                        "forget_target",
                        "rotp_harvest:stay_with",
                        "carry_up"));
        LONG_RANGE_STANDS = stringList(
                builder,
                "longRangeStands",
                List.of(
                        "rotp_harvest:harvest_stand",
                        "rotp_zbc:bad_company",
                        "rotp_zgd:green_day"));
        USE_ADDON_STANDS = builder
                .define("useAddonStands", false);
        SMALL_ANARCHY_WITH_STANDS = builder
                .comment(
                        "Allows Stand entities to receive Stand and player-power "
                                + "attachments for Debug Stand nested-power actions. "
                                + "This never adds mob goals or spawn rolls.")
                .define("smallAnarchyWithStands", false);
        DROP_STAND_DISC_FROM_MOBS = builder
                .define("dropStandDiscFromMobs", false);
        SPAWN_BOY_2_MAN = builder
                .define("spawnBoy2Man", false);
        USE_WIP_COMBO_SYSTEM = builder
                .define("useWIPComboSystem", true);
        REQUIRE_STAND_ADVANCEMENT_TO_SPAWN_MOBS_WITH_POWERS = builder
                .define(
                        "requireStandAdvancementToSpawnMobsWithPowers",
                        true);

        builder.pop();
        SPEC = builder.build();
    }

    private static ModConfigSpec.ConfigValue<List<? extends String>> stringList(
            ModConfigSpec.Builder builder,
            String key,
            List<String> defaults) {
        return builder.defineListAllowEmpty(
                key,
                defaults,
                () -> "",
                value -> value instanceof String);
    }

    private MobPowerConfig() {}
}
