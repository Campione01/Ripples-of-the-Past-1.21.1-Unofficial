package rotp.addons.pearljam;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import java.util.function.Supplier;

import rotp.core.api.RotpAddonApi;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.type.StandType;

import rotp.addons.pearljam.ability.PearlJamDiagnoseAbility;
import rotp.addons.pearljam.ability.PearlJamInfuseAbility;
import rotp.addons.pearljam.ability.PearlJamPoisonAbility;
import rotp.addons.pearljam.ability.PearlJamToggleAbility;
import rotp.addons.pearljam.ability.PearlJamToggleAbility.Setting;
import rotp.addons.pearljam.ability.PearlJamUnjectAbility;
import rotp.addons.pearljam.entity.PearlJamEntity;
import rotp.addons.pearljam.food.PearlJamDishData;
import rotp.addons.pearljam.stand.PearlJamStandType;
import rotp.addons.pearljam.state.PearlJamPlayerState;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@Mod(PearlJamAddon.MOD_ID)
public final class PearlJamAddon {
    public static final String MOD_ID = "rotp_pj";
    public static final String DIAGNOSE_NAME = "pj_diagnose";
    public static final String INJECT_NAME = "pj_inject";
    public static final String POISON_INJECT_NAME =
            "pj_poison_inject";
    public static final String UNJECT_NAME = "pj_unject";
    public static final String CRAFT_PASSIVE_NAME =
            "pj_craft_passive";
    public static final String HAND_TOGGLE_NAME =
            "pj_hand_toggle";
    public static final String SECRET_BY_CHEF_NAME =
            "pj_secret_by_chef";

    public static final double POWER = 2.0D;
    public static final double SPEED = 9.0D;
    public static final double EFFECTIVE_RANGE = 11.0D;
    public static final double MAX_RANGE = 13.0D;
    public static final double DURABILITY = 14.0D;
    public static final double PRECISION = 2.0D;
    public static final float STAND_WIDTH = 0.65F;
    public static final float STAND_HEIGHT = 1.8F;

    private static final int REQUIRED_CORE_ABI = 1;

    public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES =
            DeferredRegister.create(
                    JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<StandType> STAND_TYPES =
            DeferredRegister.create(
                    JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);
    public static final DeferredRegister.DataComponents
            DATA_COMPONENT_TYPES =
            DeferredRegister.createDataComponents(
                    Registries.DATA_COMPONENT_TYPE, MOD_ID);
    public static final DeferredRegister<AttachmentType<?>>
            ATTACHMENT_TYPES =
            DeferredRegister.create(
                    NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID);

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<PearlJamDiagnoseAbility>>
            DIAGNOSE = ABILITY_TYPES.register(
                    DIAGNOSE_NAME,
                    key -> new AbilityType<>(
                            key, PearlJamDiagnoseAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<PearlJamInfuseAbility>>
            INJECT = ABILITY_TYPES.register(
                    INJECT_NAME,
                    key -> new AbilityType<>(
                            key, PearlJamInfuseAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<PearlJamPoisonAbility>>
            POISON_INJECT = ABILITY_TYPES.register(
                    POISON_INJECT_NAME,
                    key -> new AbilityType<>(
                            key, PearlJamPoisonAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<PearlJamUnjectAbility>>
            UNJECT = ABILITY_TYPES.register(
                    UNJECT_NAME,
                    key -> new AbilityType<>(
                            key, PearlJamUnjectAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<PearlJamToggleAbility>>
            CRAFT_PASSIVE = toggleAbility(
                    CRAFT_PASSIVE_NAME,
                    Setting.CRAFT_PASSIVE);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<PearlJamToggleAbility>>
            HAND_TOGGLE = toggleAbility(
                    HAND_TOGGLE_NAME, Setting.HAND);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<PearlJamToggleAbility>>
            SECRET_BY_CHEF = toggleAbility(
                    SECRET_BY_CHEF_NAME,
                    Setting.CHEF_SIGNATURE);

    public static final DeferredHolder<
            EntityType<?>, EntityType<PearlJamEntity>>
            PEARL_JAM_ENTITY = ENTITY_TYPES.register(
                    "pearl_jam",
                    key -> EntityType.Builder
                            .<PearlJamEntity>of(
                                    PearlJamEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(STAND_WIDTH, STAND_HEIGHT)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<SoundEvent, SoundEvent>
            INJECT_SOUND = sound("pj_inject");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            UNJECT_SOUND = sound("pj_unject");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SUMMON_SOUND = sound("pj_summon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            UNSUMMON_SOUND = sound("pj_unsummon");

    public static final Supplier<DataComponentType<PearlJamDishData>>
            DISH_DATA =
            DATA_COMPONENT_TYPES.registerComponentType(
                    "pearl_jam_dish",
                    builder -> builder
                            .persistent(PearlJamDishData.CODEC)
                            .networkSynchronized(
                                    PearlJamDishData.STREAM_CODEC)
                            .cacheEncoding());

    public static final Supplier<AttachmentType<PearlJamPlayerState>>
            PLAYER_STATE =
            ATTACHMENT_TYPES.register(
                    "pearl_jam_player_state",
                    () -> AttachmentType.serializable(
                                    holder -> {
                                        if (!(holder
                                                instanceof Player)) {
                                            throw new IllegalArgumentException(
                                                    "Pearl Jam player state "
                                                            + "requires a "
                                                            + "Player holder");
                                        }
                                        return new PearlJamPlayerState();
                                    })
                            .copyOnDeath()
                            .sync(
                                    (holder, player) ->
                                            holder == player,
                                    PearlJamPlayerState.STREAM_CODEC)
                            .build());

    public static final DeferredHolder<
            StandType, PearlJamStandType>
            PEARL_JAM = STAND_TYPES.register(
                    "pearl_jam", PearlJamAddon::createStand);

    public PearlJamAddon(IEventBus modEventBus) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_VISUAL_CONTEXT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_RANDOM_WEIGHTS_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_SHOUTS_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_POWER_ACTION_SLOT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_ACTION_PORT_SLOT_V1)) {
            throw new IllegalStateException(
                    "RotP Pearl Jam requires addon ABI 1 with "
                            + "namespaced ability resources and Stand "
                            + "visual contexts and Stand random weights "
                            + "and Stand action port slots.");
        }

        ABILITY_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        DATA_COMPONENT_TYPES.register(modEventBus);
        ATTACHMENT_TYPES.register(modEventBus);
        modEventBus.addListener(PearlJamEvents::registerAttributes);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static PearlJamPlayerState state(Player player) {
        return player.getData(PLAYER_STATE);
    }

    public static void syncState(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.syncData(PLAYER_STATE);
        }
    }

    public static boolean isPearlJamUser(LivingEntity entity) {
        StandPower power =
                entity != null ? StandPower.get(entity) : null;
        return power != null
                && power.getPowerType() == PEARL_JAM.get();
    }

    public static PearlJamEntity controller(
            LivingEntity entity) {
        StandPower power =
                entity != null ? StandPower.get(entity) : null;
        return power != null
                && power.getPowerType() == PEARL_JAM.get()
                && power.isSummoned()
                && power.getSummonedStandEntity()
                        instanceof PearlJamEntity controller
                && controller.isAlive()
                && !controller.isRemoved()
                ? controller
                : null;
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(
            String path) {
        return SOUND_EVENTS.register(
                path, SoundEvent::createVariableRangeEvent);
    }

    private static DeferredHolder<
            AbilityType<?>, AbilityType<PearlJamToggleAbility>>
            toggleAbility(String name, Setting setting) {
        return ABILITY_TYPES.register(
                name,
                key -> new AbilityType<>(
                        key,
                        (type, id) -> new PearlJamToggleAbility(
                                type, id, setting)));
    }

    private static PearlJamStandType createStand(
            ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addAbility(DIAGNOSE_NAME, DIAGNOSE)
                .addAbility(INJECT_NAME, INJECT)
                .addAbility(
                        POISON_INJECT_NAME, POISON_INJECT)
                .addAbility(UNJECT_NAME, UNJECT)
                .addAbility(
                        CRAFT_PASSIVE_NAME, CRAFT_PASSIVE)
                .addAbility(HAND_TOGGLE_NAME, HAND_TOGGLE)
                .addAbility(
                        SECRET_BY_CHEF_NAME, SECRET_BY_CHEF)
                .makeControlScheme("hotbar")
                    .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
                    .addToHotbar(
                            DIAGNOSE_NAME, 0, InputMethod.HOLD)
                    .addToHotbar(
                            INJECT_NAME, 0, InputMethod.HOLD)
                    .addToHotbar(
                            POISON_INJECT_NAME,
                            0,
                            InputMethod.HOLD)
                    .addToHotbar(
                            UNJECT_NAME, 0, InputMethod.HOLD)
                    .addToHotbar(
                            HAND_TOGGLE_NAME,
                            0,
                            InputMethod.HOLD)
                    .addToHotbar(
                            SECRET_BY_CHEF_NAME,
                            0,
                            InputMethod.HOLD)
                .finalizeControlScheme()
                .makeControlScheme("keybinds")
                .finalizeControlScheme()
                .addSkill(StandUnlockableSkill.startingAbility(
                        DIAGNOSE_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        INJECT_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        POISON_INJECT_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        UNJECT_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        CRAFT_PASSIVE_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        HAND_TOGGLE_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        SECRET_BY_CHEF_NAME));

        return new PearlJamStandType(
                new StandStats.Builder()
                        .power(POWER)
                        .speed(SPEED)
                        .range(EFFECTIVE_RANGE, MAX_RANGE)
                        .durability(DURABILITY)
                        .precision(PRECISION)
                        .randomWeight(3.0D)
                        .build(),
                moveset,
                PEARL_JAM_ENTITY.get(),
                id)
                .standControlType(StandControlType.COLONY)
                .manualControl(false)
                .distanceStrengthDecay(false)
                .standDimensions(STAND_WIDTH, STAND_HEIGHT)
                .summonSound(() -> SUMMON_SOUND)
                .unsummonSound(() -> UNSUMMON_SOUND);
    }
}
