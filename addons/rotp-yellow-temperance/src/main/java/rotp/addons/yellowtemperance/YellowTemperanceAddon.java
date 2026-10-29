package rotp.addons.yellowtemperance;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import rotp.core.api.RotpAddonApi;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.util.sound.OstSoundList;

import rotp.addons.yellowtemperance.ability.YellowTemperanceAbsorptionAbility;
import rotp.addons.yellowtemperance.ability.YellowTemperanceBlockAbility;
import rotp.addons.yellowtemperance.ability.YellowTemperanceClearDebuffsAbility;
import rotp.addons.yellowtemperance.ability.YellowTemperanceDisguiseAbility;
import rotp.addons.yellowtemperance.ability.YellowTemperanceDisguiseDisableAbility;
import rotp.addons.yellowtemperance.ability.YellowTemperancePunchAbility;
import rotp.addons.yellowtemperance.ability.YellowTemperanceToggleDollAbility;
import rotp.addons.yellowtemperance.disguise.DisguiseMode;
import rotp.addons.yellowtemperance.disguise.YellowTemperanceScaleTypes;
import rotp.addons.yellowtemperance.effect.YellowTemperanceEffect;
import rotp.addons.yellowtemperance.entity.YellowTemperanceControllerEntity;
import rotp.addons.yellowtemperance.network.YellowTemperancePayloads;
import rotp.addons.yellowtemperance.stand.YellowTemperanceStandType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(YellowTemperanceAddon.MOD_ID)
public final class YellowTemperanceAddon {
    public static final String MOD_ID = "rotp_yellowtemperance";
    private static final int REQUIRED_CORE_ABI = 1;

    public static final String PUNCH_NAME =
            "yellow_temperance_punch";
    public static final String BLOCK_NAME =
            "yellow_temperance_block";
    public static final String ABSORPTION_NAME =
            "yellow_temperance_absorption";
    public static final String CLEAR_NAME =
            "yellow_temperance_clear_debuffs";
    public static final String DISGUISE_NAME =
            "yellow_temperance_disguise";
    public static final String DISGUISE_DISABLE_NAME =
            "yellow_temperance_disguise_disable";
    public static final String DISGUISE_PLAYER_NAME =
            "yellow_temperance_disguise_player";
    public static final String DISGUISE_PLAYER_DISABLE_NAME =
            "yellow_temperance_disguise_player_disable";
    public static final String DISGUISE_STAND_NAME =
            "yellow_temperance_disguise_stand";
    public static final String DISGUISE_STAND_DISABLE_NAME =
            "yellow_temperance_disguise_stand_disable";
    public static final String TOGGLE_DOLL_NAME =
            "yellow_temperance_toggle_doll";

    public static final DeferredRegister<AbilityType<?>>
            ABILITY_TYPES = DeferredRegister.create(
                    JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<EntityType<?>>
            ENTITY_TYPES = DeferredRegister.create(
                    Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<MobEffect>
            MOB_EFFECTS = DeferredRegister.create(
                    Registries.MOB_EFFECT, MOD_ID);
    public static final DeferredRegister<SoundEvent>
            SOUND_EVENTS = DeferredRegister.create(
                    Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<StandType>
            STAND_TYPES = DeferredRegister.create(
                    JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<YellowTemperancePunchAbility>>
            PUNCH = ABILITY_TYPES.register(
                    PUNCH_NAME,
                    key -> new AbilityType<>(
                            key, YellowTemperancePunchAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<YellowTemperanceBlockAbility>>
            BLOCK = ABILITY_TYPES.register(
                    BLOCK_NAME,
                    key -> new AbilityType<>(
                            key, YellowTemperanceBlockAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<YellowTemperanceAbsorptionAbility>>
            ABSORPTION = ABILITY_TYPES.register(
                    ABSORPTION_NAME,
                    key -> new AbilityType<>(
                            key,
                            YellowTemperanceAbsorptionAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<YellowTemperanceClearDebuffsAbility>>
            CLEAR_DEBUFFS = ABILITY_TYPES.register(
                    CLEAR_NAME,
                    key -> new AbilityType<>(
                            key,
                            YellowTemperanceClearDebuffsAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<YellowTemperanceDisguiseAbility>>
            DISGUISE = disguise(
                    DISGUISE_NAME,
                    DisguiseMode.ENTITY,
                    DISGUISE_DISABLE_NAME,
                    2);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<YellowTemperanceDisguiseDisableAbility>>
            DISGUISE_DISABLE = disable(
                    DISGUISE_DISABLE_NAME, 2);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<YellowTemperanceDisguiseAbility>>
            DISGUISE_PLAYER = disguise(
                    DISGUISE_PLAYER_NAME,
                    DisguiseMode.PLAYER,
                    DISGUISE_PLAYER_DISABLE_NAME,
                    3);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<YellowTemperanceDisguiseDisableAbility>>
            DISGUISE_PLAYER_DISABLE = disable(
                    DISGUISE_PLAYER_DISABLE_NAME, 3);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<YellowTemperanceDisguiseAbility>>
            DISGUISE_STAND = disguise(
                    DISGUISE_STAND_NAME,
                    DisguiseMode.STAND,
                    DISGUISE_STAND_DISABLE_NAME,
                    3);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<YellowTemperanceDisguiseDisableAbility>>
            DISGUISE_STAND_DISABLE = disable(
                    DISGUISE_STAND_DISABLE_NAME, 3);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<YellowTemperanceToggleDollAbility>>
            TOGGLE_DOLL = ABILITY_TYPES.register(
                    TOGGLE_DOLL_NAME,
                    key -> new AbilityType<>(
                            key,
                            YellowTemperanceToggleDollAbility::new));

    public static final DeferredHolder<
            EntityType<?>, EntityType<YellowTemperanceControllerEntity>>
            CONTROLLER = ENTITY_TYPES.register(
                    "yellow_temperance_controller",
                    key -> EntityType.Builder
                            .<YellowTemperanceControllerEntity>of(
                                    YellowTemperanceControllerEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(
                                    YellowTemperancePhase1Spec
                                            .CONTROLLER_WIDTH,
                                    YellowTemperancePhase1Spec
                                            .CONTROLLER_HEIGHT)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<MobEffect, MobEffect>
            PROTECTION = MOB_EFFECTS.register(
                    "yellow_temperance_protection",
                    () -> new YellowTemperanceEffect(
                            MobEffectCategory.BENEFICIAL,
                            0xFFA500));
    public static final DeferredHolder<MobEffect, MobEffect>
            EROSION = MOB_EFFECTS.register(
                    "yellow_temperance_erosion",
                    () -> new YellowTemperanceEffect(
                            MobEffectCategory.HARMFUL,
                            0x8B8000));

    public static final DeferredHolder<SoundEvent, SoundEvent>
            YT_SUMMON = sound("yt_summon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            YT_PUNCH = sound("yt_punch");
    public static final OstSoundList YT_OST =
            new OstSoundList(id("example_stand_ost"), SOUND_EVENTS);

    public static final DeferredHolder<
            StandType, YellowTemperanceStandType>
            YELLOW_TEMPERANCE = STAND_TYPES.register(
                    "yellow_temperance",
                    YellowTemperanceAddon::createStand);

    public YellowTemperanceAddon(
            IEventBus modEventBus, ModContainer modContainer) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_VISUAL_CONTEXT_V1)
                // attack-strength reset on perform (R146 core)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ENTITY_ACTION_HOOKS_V1)
                // Block, Absorption and Clear Debuffs tick through the
                // controller's summon lock (K5-44 core) and its stun (K5-45)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ACTION_SUMMON_LOCK_HOOK_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ACTION_STAND_STUN_HOOK_V1)
                // and run in the user's Stand-power slot (K5-48)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_POWER_ACTION_SLOT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_ACTION_PORT_SLOT_V1)) {
            throw new IllegalStateException(
                    "RotP Yellow Temperance requires addon ABI 1 "
                            + "with namespaced ability resources, "
                            + "local Stand visual contexts, entity "
                            + "action hooks, the action summon lock "
                            + "hook, the action Stand stun hook, the "
                            + "Stand-power action slot and the Stand "
                            + "action port slot "
                            + "(a core from the same release).");
        }
        YellowTemperanceScaleTypes.initialize();
        ABILITY_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        MOB_EFFECTS.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        modEventBus.addListener(
                YellowTemperanceGameEvents::registerAttributes);
        modEventBus.addListener(YellowTemperancePayloads::register);
        modContainer.registerConfig(
                ModConfig.Type.COMMON,
                YellowTemperanceConfig.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                MOD_ID, path);
    }

    public static boolean isYellowTemperanceUser(
            LivingEntity user) {
        StandPower power = StandPower.get(user);
        return power != null
                && power.getPowerType() == YELLOW_TEMPERANCE.get();
    }

    public static YellowTemperanceControllerEntity controller(
            LivingEntity user) {
        StandPower power = user != null ? StandPower.get(user) : null;
        if (power == null
                || power.getPowerType()
                        != YELLOW_TEMPERANCE.get()
                || !(power.getSummonedStandEntity()
                        instanceof YellowTemperanceControllerEntity
                        controller)
                || controller.getUser() != user
                || !controller.isAlive()
                || controller.isRemoved()) {
            return null;
        }
        return controller;
    }

    private static DeferredHolder<
            AbilityType<?>, AbilityType<YellowTemperanceDisguiseAbility>>
            disguise(
                    String name,
                    DisguiseMode mode,
                    String disableName,
                    int resolveLevel) {
        return ABILITY_TYPES.register(
                name,
                key -> new AbilityType<>(
                        key,
                        (type, id) ->
                                new YellowTemperanceDisguiseAbility(
                                        type,
                                        id,
                                        mode,
                                        disableName,
                                        resolveLevel)));
    }

    private static DeferredHolder<
            AbilityType<?>,
            AbilityType<YellowTemperanceDisguiseDisableAbility>>
            disable(String name, int resolveLevel) {
        return ABILITY_TYPES.register(
                name,
                key -> new AbilityType<>(
                        key,
                        (type, id) ->
                                new YellowTemperanceDisguiseDisableAbility(
                                        type, id, resolveLevel)));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(
            String name) {
        return SOUND_EVENTS.register(
                name, SoundEvent::createVariableRangeEvent);
    }

    private static YellowTemperanceStandType createStand(
            ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addAbility(
                        PUNCH_NAME,
                        PUNCH,
                        punch -> punch.punchImpactSound(YT_PUNCH))
                .addAbility(BLOCK_NAME, BLOCK)
                .addAbility(ABSORPTION_NAME, ABSORPTION)
                .addAbility(CLEAR_NAME, CLEAR_DEBUFFS)
                .addAbility(DISGUISE_NAME, DISGUISE)
                .addAbility(
                        DISGUISE_DISABLE_NAME, DISGUISE_DISABLE)
                .addAbility(
                        DISGUISE_PLAYER_NAME, DISGUISE_PLAYER)
                .addAbility(
                        DISGUISE_PLAYER_DISABLE_NAME,
                        DISGUISE_PLAYER_DISABLE)
                .addAbility(
                        DISGUISE_STAND_NAME, DISGUISE_STAND)
                .addAbility(
                        DISGUISE_STAND_DISABLE_NAME,
                        DISGUISE_STAND_DISABLE)
                .addAbility(TOGGLE_DOLL_NAME, TOGGLE_DOLL)
                .makeControlScheme("hotbar")
                    .bind(
                            PUNCH_NAME,
                            InputMethod.CLICK,
                            InputKey.LMB)
                    .bind(
                            BLOCK_NAME,
                            InputMethod.HOLD,
                            InputKey.RMB)
                    .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
                    .addToHotbar(
                            ABSORPTION_NAME, 0, InputMethod.HOLD)
                    .addToHotbar(
                            CLEAR_NAME, 0, InputMethod.HOLD)
                    .addToHotbar(
                            DISGUISE_NAME, 0, InputMethod.CLICK)
                    .addToHotbar(
                            DISGUISE_PLAYER_NAME,
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            DISGUISE_STAND_NAME,
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            TOGGLE_DOLL_NAME,
                            0,
                            InputMethod.CLICK)
                .finalizeControlScheme()
                .makeControlScheme("keybinds")
                    .bind(
                            PUNCH_NAME,
                            InputMethod.CLICK,
                            InputKey.LMB)
                    .bind(
                            BLOCK_NAME,
                            InputMethod.HOLD,
                            InputKey.RMB)
                .finalizeControlScheme();

        for (String ability : new String[] {
                PUNCH_NAME,
                BLOCK_NAME,
                ABSORPTION_NAME,
                CLEAR_NAME,
                DISGUISE_NAME,
                DISGUISE_DISABLE_NAME,
                DISGUISE_PLAYER_NAME,
                DISGUISE_PLAYER_DISABLE_NAME,
                DISGUISE_STAND_NAME,
                DISGUISE_STAND_DISABLE_NAME,
                TOGGLE_DOLL_NAME
        }) {
            moveset.addSkill(
                    StandUnlockableSkill.startingAbility(ability));
        }

        return new YellowTemperanceStandType(
                new StandStats.Builder()
                        .power(YellowTemperancePhase1Spec.POWER)
                        .speed(YellowTemperancePhase1Spec.SPEED)
                        .range(
                                YellowTemperancePhase1Spec
                                        .EFFECTIVE_RANGE,
                                YellowTemperancePhase1Spec.MAX_RANGE)
                        .durability(
                                YellowTemperancePhase1Spec.DURABILITY)
                        .precision(
                                YellowTemperancePhase1Spec.PRECISION)
                        .randomWeight(1.0D)
                        .build(),
                moveset,
                CONTROLLER.get(),
                id)
                .standControlType(StandControlType.PHENOMENON)
                .manualControl(false)
                .distanceStrengthDecay(false)
                .standDimensions(
                        YellowTemperancePhase1Spec.CONTROLLER_WIDTH,
                        YellowTemperancePhase1Spec.CONTROLLER_HEIGHT)
                .summonShout(() -> YT_SUMMON)
                .summonSound(() -> ModSoundEvents.STAND_SUMMON)
                .unsummonSound(() -> ModSoundEvents.STAND_UNSUMMON)
                .ost(() -> YT_OST);
    }
}
