package rotp.addons.neo_stand;

import java.util.function.BiFunction;

import rotp.core.api.RotpAddonApi;
import rotp.core.api.stand.StandMovesetExtensions;
import rotp.core.api.stand.StandMovesetExtensions.StandSkill;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputMethod;

import rotp.addons.neo_stand.ability.AutoStringsAbility;
import rotp.addons.neo_stand.ability.BulletTimeAbility;
import rotp.addons.neo_stand.ability.BulletTimeCancelAbility;
import rotp.addons.neo_stand.ability.CircleKnivesAbility;
import rotp.addons.neo_stand.ability.DevolutionAbility;
import rotp.addons.neo_stand.ability.FingerSweepAbility;
import rotp.addons.neo_stand.ability.PlantAttackAbility;
import rotp.addons.neo_stand.ability.TimeStopTeleportationAbility;
import rotp.addons.neo_stand.effect.BulletTimeEffect;
import rotp.addons.neo_stand.entity.PlantAttackEntity;
import rotp.addons.neo_stand.network.BulletTimeSyncPayload;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(NeoStandAddon.MOD_ID)
public final class NeoStandAddon {
    public static final String MOD_ID = "neojojostand";
    private static final int REQUIRED_CORE_ABI = 1;

    public static final String PLANT_ATTACK =
            "gold_experience_plant_attack";
    public static final String FINGER_SWEEP =
            "star_platinum_finger_sweep";
    public static final String BULLET_TIME =
            "star_platinum_bullet_time";
    public static final String BULLET_TIME_CANCEL =
            "star_platinum_bullet_time_cancel";
    public static final String AUTO_STRINGS =
            "hierophant_green_auto_strings";
    public static final String CIRCLE_KNIVES =
            "the_world_circle_knives_barrier";
    public static final String DEVOLUTION =
            "the_world_devolution";
    public static final String TIME_STOP_TELEPORTATION =
            "the_world_time_stop_teleportation";

    public static final ResourceLocation STAR_PLATINUM =
            coreId("star_platinum");
    public static final ResourceLocation GOLD_EXPERIENCE =
            coreId("gold_experience");
    public static final ResourceLocation HIEROPHANT_GREEN =
            coreId("hierophant_green");
    public static final ResourceLocation THE_WORLD =
            coreId("the_world");
    public static final ResourceLocation SHADOW_THE_WORLD =
            ResourceLocation.fromNamespaceAndPath(
                    "rotp_shadowtheworld", "shadowtheworld");

    public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES =
            DeferredRegister.create(
                    JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, MOD_ID);

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<FingerSweepAbility>>
            FINGER_SWEEP_ABILITY = ability(
                    FINGER_SWEEP, FingerSweepAbility::new);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<BulletTimeAbility>>
            BULLET_TIME_ABILITY = ability(
                    BULLET_TIME, BulletTimeAbility::new);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<BulletTimeCancelAbility>>
            BULLET_TIME_CANCEL_ABILITY = ability(
                    BULLET_TIME_CANCEL,
                    BulletTimeCancelAbility::new);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<PlantAttackAbility>>
            PLANT_ATTACK_ABILITY = ability(
                    PLANT_ATTACK, PlantAttackAbility::new);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<AutoStringsAbility>>
            AUTO_STRINGS_ABILITY = ability(
                    AUTO_STRINGS, AutoStringsAbility::new);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<CircleKnivesAbility>>
            CIRCLE_KNIVES_ABILITY = ability(
                    CIRCLE_KNIVES, CircleKnivesAbility::new);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<DevolutionAbility>>
            DEVOLUTION_ABILITY = ability(
                    DEVOLUTION, DevolutionAbility::new);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<TimeStopTeleportationAbility>>
            TIME_STOP_TELEPORTATION_ABILITY = ability(
                    TIME_STOP_TELEPORTATION,
                    TimeStopTeleportationAbility::new);
    public static final DeferredHolder<MobEffect, BulletTimeEffect>
            BULLET_TIME_EFFECT = EFFECTS.register(
                    "bullet_time", BulletTimeEffect::new);

    public static final DeferredHolder<
            EntityType<?>, EntityType<PlantAttackEntity>>
            PLANT_ATTACK_ENTITY = ENTITY_TYPES.register(
                    "plant_attack",
                    key -> EntityType.Builder
                            .<PlantAttackEntity>of(
                                    PlantAttackEntity::new,
                                    MobCategory.MISC)
                            .noSummon()
                            .sized(0.5F, 0.5F)
                            .clientTrackingRange(64)
                            .updateInterval(1)
                            .build(key.toString()));

    public NeoStandAddon(IEventBus modEventBus) {
        requireCoreApi();
        ABILITY_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        EFFECTS.register(modEventBus);
        modEventBus.addListener(BulletTimeSyncPayload::register);
        modEventBus.addListener(this::commonSetup);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            StandMovesetExtensions.register(
                    StandMovesetExtensions.builder(
                                    STAR_PLATINUM,
                                    id("star_platinum_extension"),
                                    100)
                            .addAbility(
                                    FINGER_SWEEP,
                                    FINGER_SWEEP_ABILITY.getId(),
                                    FINGER_SWEEP_ABILITY)
                            .addSkill(StandSkill.startingAbility(
                                    FINGER_SWEEP))
                            .appendToHotbar(
                                    "hotbar",
                                    0,
                                    FINGER_SWEEP,
                                    InputMethod.CLICK)
                            .addAbility(
                                    BULLET_TIME,
                                    BULLET_TIME_ABILITY.getId(),
                                    BULLET_TIME_ABILITY)
                            .addSkill(StandSkill.startingAbility(
                                    BULLET_TIME))
                            .appendToHotbar(
                                    "hotbar",
                                    0,
                                    BULLET_TIME,
                                    InputMethod.CLICK)
                            .addAbility(
                                    BULLET_TIME_CANCEL,
                                    BULLET_TIME_CANCEL_ABILITY.getId(),
                                    BULLET_TIME_CANCEL_ABILITY)
                            .addSkill(StandSkill.startingAbility(
                                    BULLET_TIME_CANCEL))
                            .build());
            registerSingleAbility(
                    GOLD_EXPERIENCE,
                    id("gold_experience_extension"),
                    PLANT_ATTACK,
                    PLANT_ATTACK_ABILITY);
            registerSingleAbility(
                    HIEROPHANT_GREEN,
                    id("hierophant_green_extension"),
                    AUTO_STRINGS,
                    AUTO_STRINGS_ABILITY);

            StandMovesetExtensions.register(
                    StandMovesetExtensions.builder(
                                    THE_WORLD,
                                    id("the_world_extension"),
                                    100)
                            .addAbility(
                                    CIRCLE_KNIVES,
                                    CIRCLE_KNIVES_ABILITY.getId(),
                                    CIRCLE_KNIVES_ABILITY)
                            .addSkill(StandSkill.startingAbility(
                                    CIRCLE_KNIVES))
                            .appendToHotbar(
                                    "hotbar",
                                    0,
                                    CIRCLE_KNIVES,
                                    InputMethod.CLICK)
                            .addAbility(
                                    DEVOLUTION,
                                    DEVOLUTION_ABILITY.getId(),
                                    DEVOLUTION_ABILITY)
                            .addSkill(StandSkill.startingAbility(
                                    DEVOLUTION))
                            .appendToHotbar(
                                    "hotbar",
                                    0,
                                    DEVOLUTION,
                                    InputMethod.CLICK)
                            .addAbility(
                                    TIME_STOP_TELEPORTATION,
                                    TIME_STOP_TELEPORTATION_ABILITY.getId(),
                                    TIME_STOP_TELEPORTATION_ABILITY)
                            .addSkill(StandSkill.startingAbility(
                                    TIME_STOP_TELEPORTATION))
                            .appendToHotbar(
                                    "hotbar",
                                    0,
                                    TIME_STOP_TELEPORTATION,
                                    InputMethod.CLICK)
                            .build());
        });
    }

    private static void registerSingleAbility(
            ResourceLocation targetStand,
            ResourceLocation extensionId,
            String abilityName,
            DeferredHolder<
                    AbilityType<?>, ? extends AbilityType<?>>
                    abilityType) {
        StandMovesetExtensions.register(
                StandMovesetExtensions.builder(
                                targetStand,
                                extensionId,
                                100)
                        .addAbility(
                                abilityName,
                                abilityType.getId(),
                                abilityType)
                        .addSkill(
                                StandSkill.startingAbility(
                                        abilityName))
                        .appendToHotbar(
                                "hotbar",
                                0,
                                abilityName,
                                InputMethod.CLICK)
                        .build());
    }

    private static <
                    A extends Ability>
            DeferredHolder<AbilityType<?>, AbilityType<A>> ability(
                    String name,
                    BiFunction<AbilityType<A>, AbilityId, A> factory) {
        return ABILITY_TYPES.register(
                name, key -> new AbilityType<>(key, factory));
    }

    private static ResourceLocation coreId(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                "jojo_ripples", path);
    }

    private static void requireCoreApi() {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_MOVESET_EXTENSIONS_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_VISUAL_CONTEXT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ENTITY_POST_RENDER_EXTENSIONS_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_CLIENT_REGIONAL_TIME_DILATION_POLICIES_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_POWER_ACTION_SLOT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_ACTION_PORT_SLOT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ACTION_STAND_OFFSET_HOOK_V1)) {
            throw new IllegalStateException(
                    "NEOJOJOSTAND requires ROTP addon ABI 1 with "
                            + "namespaced ability resources, Stand "
                            + "moveset extensions, Stand visual "
                            + "contexts, entity post-render extensions, "
                            + "regional client time dilation, "
                            + "Stand action port slots, and action Stand "
                            + "offsets.");
        }
    }
}
