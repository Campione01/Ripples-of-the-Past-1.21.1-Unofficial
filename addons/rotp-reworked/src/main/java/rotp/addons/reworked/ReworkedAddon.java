package rotp.addons.reworked;

import java.util.List;
import java.util.function.Supplier;

import rotp.core.api.RotpAddonApi;
import rotp.core.api.power.PowerSkillUnlocks;
import rotp.core.api.stand.StandMovesetExtensions;
import rotp.core.api.timestop.TimeStopBehaviorPolicies;
import rotp.core.core.JojoRegistries;
import rotp.core.entityattachment.custom_effect.EntityCustomEffectType;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.init.power.ModStands;
import rotp.core.powersystem.ability.AbilityType;

import rotp.addons.reworked.ability.ReworkedStandAbilities;
import rotp.addons.reworked.effect.SlowBurnEffect;
import rotp.addons.reworked.state.ReworkedLivingState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@Mod(ReworkedAddon.MOD_ID)
public final class ReworkedAddon {
    public static final String MOD_ID = "rejojo";
    private static final int REQUIRED_CORE_ABI = 1;
    public static final PowerSkillUnlocks.Owner POWER_SKILL_UNLOCKS =
            PowerSkillUnlocks.register(id("projectile_time_stop"));
    public static final PowerSkillUnlocks.Owner MAGICIANS_RED_TRAINING_UNLOCKS =
            PowerSkillUnlocks.register(id("magicians_red_punch_training"));

    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, MOD_ID);
    public static final DeferredRegister<AbilityType<?>>
            ABILITY_TYPES = DeferredRegister.create(
                    JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<AttachmentType<?>>
            ATTACHMENT_TYPES = DeferredRegister.create(
                    NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID);
    public static final DeferredRegister<EntityCustomEffectType<?>>
            STAND_EFFECT_TYPES = DeferredRegister.create(
                    JojoRegistries.STAND_EFFECTS_REG, MOD_ID);

    public static final DeferredHolder<MobEffect, SlowBurnEffect>
            SLOW_BURN = MOB_EFFECTS.register(
                    "slowburn", SlowBurnEffect::new);
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<ReworkedStandAbilities.ReworkedTimeStopAbility>>
            STAR_PLATINUM_TIME_STOP = ABILITY_TYPES.register(
                    "star_platinum_time_stop",
                    key -> new AbilityType<>(
                            key,
                            ReworkedStandAbilities
                                    .ReworkedTimeStopAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<ReworkedStandAbilities.ReworkedTimeStopBlinkAbility>>
            STAR_PLATINUM_TIME_STOP_BLINK = ABILITY_TYPES.register(
                    "star_platinum_ts_blink",
                    key -> new AbilityType<>(
                            key,
                            ReworkedStandAbilities
                                    .ReworkedTimeStopBlinkAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<ReworkedStandAbilities.ReworkedTimeResumeAbility>>
            STAR_PLATINUM_TIME_RESUME = ABILITY_TYPES.register(
                    "star_platinum_time_resume",
                    key -> new AbilityType<>(
                            key,
                            ReworkedStandAbilities
                                    .ReworkedTimeResumeAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<ReworkedStandAbilities.ReworkedStarPlatinumUppercutAbility>>
            STAR_PLATINUM_UPPERCUT = ABILITY_TYPES.register(
                    "star_platinum_uppercut",
                    key -> new AbilityType<>(
                            key,
                            ReworkedStandAbilities
                                    .ReworkedStarPlatinumUppercutAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<ReworkedStandAbilities.ReworkedStarPlatinumHeavyPunchAbility>>
            STAR_PLATINUM_HEAVY_PUNCH = ABILITY_TYPES.register(
                    "star_platinum_heavy_punch",
                    key -> new AbilityType<>(
                            key,
                            ReworkedStandAbilities
                                    .ReworkedStarPlatinumHeavyPunchAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<ReworkedStandAbilities.ReworkedStarPlatinumBlinkPunchAbility>>
            STAR_PLATINUM_HEAVY_PUNCH_BLINK =
                    ABILITY_TYPES.register(
                            "star_platinum_heavy_punch_ts",
                            key -> new AbilityType<>(
                                    key,
                                    ReworkedStandAbilities
                                            .ReworkedStarPlatinumBlinkPunchAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<ReworkedStandAbilities.ReworkedStarPlatinumBlinkPunchAbility>>
            STAR_PLATINUM_UPPERCUT_BLINK =
                    ABILITY_TYPES.register(
                            "star_platinum_uppercut_ts",
                            key -> new AbilityType<>(
                                    key,
                                    ReworkedStandAbilities
                                            .ReworkedStarPlatinumBlinkPunchAbility::new));
    // 1.16 STAR_PLATINUM_HEAVY_PUNCH_BLINK and STAR_PLATINUM_UPPERCUT_BLINK:
    // modifiers of the Stand's running task
    public static final DeferredHolder<
            EntityCustomEffectType<?>,
            EntityCustomEffectType<ReworkedStandAbilities.StarPlatinumBlinkModifier>>
            STAR_PLATINUM_BLINK_MODIFIER = STAND_EFFECT_TYPES.register(
                    "star_platinum_heavy_punch_ts",
                    key -> new EntityCustomEffectType<>(
                            key,
                            ReworkedStandAbilities
                                    .StarPlatinumBlinkModifier::new));
    public static final DeferredHolder<
            EntityCustomEffectType<?>,
            EntityCustomEffectType<ReworkedStandAbilities.StarPlatinumBlinkModifier>>
            STAR_PLATINUM_UPPERCUT_BLINK_MODIFIER = STAND_EFFECT_TYPES.register(
                    "star_platinum_uppercut_ts",
                    key -> new EntityCustomEffectType<>(
                            key,
                            ReworkedStandAbilities
                                    .StarPlatinumBlinkModifier::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<ReworkedStandAbilities.ReworkedTimeStopAbility>>
            THE_WORLD_TIME_STOP = ABILITY_TYPES.register(
                    "the_world_time_stop",
                    key -> new AbilityType<>(
                            key,
                            ReworkedStandAbilities
                                    .ReworkedTimeStopAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<ReworkedStandAbilities.ReworkedTimeStopBlinkAbility>>
            THE_WORLD_TIME_STOP_BLINK = ABILITY_TYPES.register(
                    "the_world_ts_blink",
                    key -> new AbilityType<>(
                            key,
                            ReworkedStandAbilities
                                    .ReworkedTimeStopBlinkAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<ReworkedStandAbilities.ReworkedTimeResumeAbility>>
            THE_WORLD_TIME_RESUME = ABILITY_TYPES.register(
                    "the_world_time_resume",
                    key -> new AbilityType<>(
                            key,
                            ReworkedStandAbilities
                                    .ReworkedTimeResumeAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<ReworkedStandAbilities.ReworkedTheWorldTSPunchAbility>>
            THE_WORLD_TS_PUNCH = ABILITY_TYPES.register(
                    "the_world_ts_punch",
                    key -> new AbilityType<>(
                            key,
                            ReworkedStandAbilities
                                    .ReworkedTheWorldTSPunchAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<ReworkedStandAbilities.ReworkedTheWorldBlinkRecoveryAbility>>
            THE_WORLD_BLINK_RECOVERY = ABILITY_TYPES.register(
                    "the_world_ts_blink_recovery",
                    key -> new AbilityType<>(
                            key,
                            ReworkedStandAbilities
                                    .ReworkedTheWorldBlinkRecoveryAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<ReworkedStandAbilities.ReworkedMagiciansRedPunchAbility>>
            MAGICIANS_RED_PUNCH = ABILITY_TYPES.register(
                    "magicians_red_punch",
                    key -> new AbilityType<>(
                            key,
                            ReworkedStandAbilities
                                    .ReworkedMagiciansRedPunchAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<ReworkedStandAbilities.ReworkedMagiciansRedKickAbility>>
            MAGICIANS_RED_KICK = ABILITY_TYPES.register(
                    "magicians_red_kick",
                    key -> new AbilityType<>(
                            key,
                            ReworkedStandAbilities
                                    .ReworkedMagiciansRedKickAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<ReworkedStandAbilities.ReworkedMagiciansRedHeavyPunchAbility>>
            MAGICIANS_RED_HEAVY_PUNCH = ABILITY_TYPES.register(
                    "magicians_red_heavy_punch",
                    key -> new AbilityType<>(
                            key,
                            ReworkedStandAbilities
                                    .ReworkedMagiciansRedHeavyPunchAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<ReworkedStandAbilities.ReworkedMagiciansRedFlameBurstAbility>>
            MAGICIANS_RED_FLAME_BURST = ABILITY_TYPES.register(
                    "magicians_red_flame_burst",
                    key -> new AbilityType<>(
                            key,
                            ReworkedStandAbilities
                                    .ReworkedMagiciansRedFlameBurstAbility::new));
    public static final Supplier<AttachmentType<ReworkedLivingState>>
            LIVING_STATE = ATTACHMENT_TYPES.register(
                    "living_state",
                    () -> AttachmentType.serializable(
                                    holder -> holder instanceof LivingEntity
                                            ? new ReworkedLivingState()
                                            : null)
                            .build());

    public ReworkedAddon(
            IEventBus modEventBus, ModContainer modContainer) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_TIME_STOP_LIFECYCLE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_MOVESET_EXTENSIONS_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_MOVESET_REPLACEMENTS_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_EXPLICIT_POWER_SKILL_UNLOCKS_V1)
                // blink base costs, blink punch training (R146 core)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_TIME_STOP_ABILITY_TUNING_V1)
                // TS punch priced from the Reworked blink (R147 core)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_TIME_STOP_PUNCH_TUNING_V1)) {
            throw new IllegalStateException(
                    "RotP Reworked requires addon ABI 1 with time-stop "
                            + "lifecycle and Stand moveset extension "
                            + "and replacement plus explicit power-skill "
                            + "unlock and time-stop ability and TS punch "
                            + "tuning feature negotiation (a core from "
                            + "the same release).");
        }
        // the TS blink recovery calls Ability.checkPerformerStun (R148 core)
        if (!RotpAddonApi.supportsFeature(
                RotpAddonApi.FEATURE_ABILITY_PERFORMER_STUN_V1)) {
            throw new IllegalStateException(
                    "RotP Reworked requires the core performer stun "
                            + "check (a core from the same release).");
        }
        // the blink on another running Stand action modifies that action
        // (R770 core)
        if (!RotpAddonApi.supportsFeature(
                RotpAddonApi.FEATURE_STAND_ACTION_MODIFIERS_V1)) {
            throw new IllegalStateException(
                    "RotP Reworked requires the core Stand action "
                            + "modifiers (a core from the same release).");
        }
        // the legacy time stop cooldown finds its starter in the user's
        // Stand-power slot (R770 core)
        if (!RotpAddonApi.supportsFeature(
                RotpAddonApi.FEATURE_STAND_POWER_ACTION_SLOT_V1)) {
            throw new IllegalStateException(
                    "RotP Reworked requires the core Stand-power action "
                            + "slot (a core from the same release).");
        }

        ABILITY_TYPES.register(modEventBus);
        STAND_EFFECT_TYPES.register(modEventBus);
        MOB_EFFECTS.register(modEventBus);
        ATTACHMENT_TYPES.register(modEventBus);
        modContainer.registerConfig(
                ModConfig.Type.COMMON, ReworkedConfig.SPEC);
        modContainer.registerConfig(
                ModConfig.Type.CLIENT, ReworkedConfig.CLIENT_SPEC);
        registerMovesetReplacements();
        // 1.16 RE_*_TIME_STOP staminaCost(300); the per-tick 8.875 is set
        // in ReworkedGameEvents.onTimeStopPreStart.
        TimeStopBehaviorPolicies.register(
                id("star_platinum_time_stop_cost"),
                ModStands.STAR_PLATINUM.getId(),
                ReworkedStandAbilities.TIME_STOP_COST_POLICY);
        TimeStopBehaviorPolicies.register(
                id("the_world_time_stop_cost"),
                ModStands.THE_WORLD.getId(),
                ReworkedStandAbilities.TIME_STOP_COST_POLICY);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static ReworkedLivingState state(LivingEntity entity) {
        return entity.getData(LIVING_STATE);
    }

    private static void registerMovesetReplacements() {
        StandMovesetExtensions.register(
                StandMovesetExtensions.builder(
                                ModStands.STAR_PLATINUM.getId(),
                                id("star_platinum_reworked_moveset"),
                                100)
                        .replaceAbility(
                                "time_stop",
                                ModStandAbilities.TIME_STOP.getId(),
                                STAR_PLATINUM_TIME_STOP.getId(),
                                STAR_PLATINUM_TIME_STOP::get)
                        // 1.16 RE_STAR_PLATINUM_TIME_STOP_BLINK: priced
                        // from the Reworked time stop's 300 / 8.875
                        .replaceAbility(
                                "time_stop_blink",
                                ModStandAbilities.TIME_STOP_BLINK.getId(),
                                STAR_PLATINUM_TIME_STOP_BLINK.getId(),
                                STAR_PLATINUM_TIME_STOP_BLINK::get)
                        .replaceAbility(
                                "heavy_punch",
                                ModStandAbilities.HEAVY_PUNCH.getId(),
                                STAR_PLATINUM_HEAVY_PUNCH.getId(),
                                STAR_PLATINUM_HEAVY_PUNCH::get)
                        .replaceAbility(
                                "finisher_uppercut",
                                ModStandAbilities.HEAVY_UPPERCUT.getId(),
                                STAR_PLATINUM_UPPERCUT.getId(),
                                STAR_PLATINUM_UPPERCUT::get)
                        .addAbility(
                                ReworkedStandAbilities
                                        .STAR_PLATINUM_BLINK_HEAVY_NAME,
                                STAR_PLATINUM_HEAVY_PUNCH_BLINK.getId(),
                                STAR_PLATINUM_HEAVY_PUNCH_BLINK::get)
                        .addAbility(
                                ReworkedStandAbilities
                                        .STAR_PLATINUM_BLINK_UPPERCUT_NAME,
                                STAR_PLATINUM_UPPERCUT_BLINK.getId(),
                                STAR_PLATINUM_UPPERCUT_BLINK::get)
                        .build());
        StandMovesetExtensions.register(
                StandMovesetExtensions.builder(
                                ModStands.THE_WORLD.getId(),
                                id("the_world_reworked_moveset"),
                                100)
                        .replaceAbility(
                                "time_stop",
                                ModStandAbilities.TIME_STOP.getId(),
                                THE_WORLD_TIME_STOP.getId(),
                                THE_WORLD_TIME_STOP::get)
                        // 1.16 RE_THE_WORLD_TIME_STOP_BLINK: 300 / 8.875,
                        // behind the target
                        .replaceAbility(
                                "time_stop_blink",
                                ModStandAbilities.TIME_STOP_BLINK.getId(),
                                THE_WORLD_TIME_STOP_BLINK.getId(),
                                THE_WORLD_TIME_STOP_BLINK::get)
                        .replaceAbility(
                                ReworkedStandAbilities
                                        .THE_WORLD_TS_PUNCH_NAME,
                                ModStandAbilities.TW_TS_PUNCH.getId(),
                                THE_WORLD_TS_PUNCH.getId(),
                                THE_WORLD_TS_PUNCH::get)
                        .addAbility(
                                ReworkedStandAbilities
                                        .THE_WORLD_BLINK_RECOVERY_NAME,
                                THE_WORLD_BLINK_RECOVERY.getId(),
                                THE_WORLD_BLINK_RECOVERY::get)
                        .build());
        StandMovesetExtensions.register(
                StandMovesetExtensions.builder(
                                ModStands.MAGICIANS_RED.getId(),
                                id("magicians_red_reworked_moveset"),
                                100)
                        .replaceAbility(
                                "punch",
                                ModStandAbilities.PUNCH.getId(),
                                MAGICIANS_RED_PUNCH.getId(),
                                MAGICIANS_RED_PUNCH::get)
                        .replaceAbility(
                                "heavy_punch",
                                ModStandAbilities.HEAVY_PUNCH.getId(),
                                MAGICIANS_RED_HEAVY_PUNCH.getId(),
                                MAGICIANS_RED_HEAVY_PUNCH::get)
                        .replaceAbility(
                                "kick",
                                ModStandAbilities.MR_KICK.getId(),
                                MAGICIANS_RED_KICK.getId(),
                                MAGICIANS_RED_KICK::get)
                        .replaceAbility(
                                "flame_burst",
                                ModStandAbilities.MR_FLAME_BURST.getId(),
                                MAGICIANS_RED_FLAME_BURST.getId(),
                                MAGICIANS_RED_FLAME_BURST::get)
                        // 1.16 RE_MAGICIANS_RED_FLAME_BURST: noResolveUnlock(),
                        // unlocked by the Reworked punch's full training
                        .addSkill(new StandMovesetExtensions.StandSkill(
                                ReworkedStandAbilities
                                        .MAGICIANS_RED_FLAME_BURST_SKILL,
                                0, false, true, List.of(), List.of()))
                        .build());
    }
}
