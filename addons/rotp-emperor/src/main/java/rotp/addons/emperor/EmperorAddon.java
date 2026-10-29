package rotp.addons.emperor;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import java.util.function.Supplier;

import rotp.core.api.RotpAddonApi;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.type.SpecialEntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.util.sound.OstSoundList;

import rotp.addons.emperor.ability.EmperorClearTargetAbility;
import rotp.addons.emperor.ability.EmperorGiveCompatibilityAbility;
import rotp.addons.emperor.ability.EmperorSetTargetAbility;
import rotp.addons.emperor.ability.EmperorTargetModeAbility;
import rotp.addons.emperor.action.EmperorSummonAction;
import rotp.addons.emperor.entity.EmperorBulletEntity;
import rotp.addons.emperor.entity.EmperorStandEntity;
import rotp.addons.emperor.item.EmperorItem;
import rotp.addons.emperor.network.EmperorModeSyncPayload;
import rotp.addons.emperor.stand.EmperorStandType;
import rotp.addons.emperor.state.EmperorModeState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@Mod(EmperorAddon.MOD_ID)
public final class EmperorAddon {
    public static final String MOD_ID = "rotp_zemperor";
    public static final String TARGET_MODE_NAME = "target";
    public static final String SET_TARGET_NAME = "stand_target";
    public static final String CLEAR_TARGET_NAME = "no_stand_target";
    public static final String GIVE_EMPEROR_NAME = "give_emp";

    public static final double POWER = 12.0D;
    public static final double SPEED = 12.0D;
    public static final double EFFECTIVE_RANGE = 12.0D;
    public static final double MAX_RANGE = 12.0D;
    public static final double DURABILITY = 8.0D;
    public static final double PRECISION = 2.0D;
    public static final double RANDOM_WEIGHT = 1.0D;

    private static final int REQUIRED_CORE_ABI = 1;

    public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES =
            DeferredRegister.create(JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<SpecialEntityActionType>
            SPECIAL_ACTIONS = DeferredRegister.create(
                    JojoRegistries.NON_POWER_ACTIONS_REG, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, MOD_ID);
    public static final DeferredRegister<StandType> STAND_TYPES =
            DeferredRegister.create(JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(
                    NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID);

    public static final Supplier<AttachmentType<EmperorModeState>>
            EMPEROR_MODE_STATE = ATTACHMENT_TYPES.register(
                    "emperor_mode",
                    () -> AttachmentType.serializable(
                                    holder -> holder instanceof Player
                                            ? new EmperorModeState()
                                            : null)
                            .build());

    public static final DeferredHolder<
            SpecialEntityActionType, EmperorSummonAction>
            EMPEROR_SUMMON_ACTION = SPECIAL_ACTIONS.register(
                    "emperor_summon", EmperorSummonAction::new);

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<EmperorTargetModeAbility>>
            TARGET_MODE = ABILITY_TYPES.register(
                    TARGET_MODE_NAME,
                    key -> new AbilityType<>(
                            key, EmperorTargetModeAbility::new));

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<EmperorSetTargetAbility>>
            SET_TARGET = ABILITY_TYPES.register(
                    SET_TARGET_NAME,
                    key -> new AbilityType<>(
                            key, EmperorSetTargetAbility::new));

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<EmperorClearTargetAbility>>
            CLEAR_TARGET = ABILITY_TYPES.register(
                    CLEAR_TARGET_NAME,
                    key -> new AbilityType<>(
                            key, EmperorClearTargetAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<EmperorGiveCompatibilityAbility>>
            GIVE_EMPEROR = ABILITY_TYPES.register(
                    GIVE_EMPEROR_NAME,
                    key -> new AbilityType<>(
                            key,
                            EmperorGiveCompatibilityAbility::new));

    public static final DeferredHolder<
            EntityType<?>, EntityType<EmperorStandEntity>>
            EMPEROR_STAND_ENTITY = ENTITY_TYPES.register(
                    "the_emperor",
                    key -> EntityType.Builder.<EmperorStandEntity>of(
                                    EmperorStandEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.0F, 0.0F)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<
            EntityType<?>, EntityType<EmperorBulletEntity>>
            EMPEROR_BULLET_ENTITY = ENTITY_TYPES.register(
                    "emperor_bullet",
                    key -> EntityType.Builder.<EmperorBulletEntity>of(
                                    EmperorBulletEntity::new,
                                    MobCategory.MISC)
                            .noSummon()
                            .sized(0.0625F, 0.0625F)
                            .clientTrackingRange(64)
                            .updateInterval(1)
                            .build(key.toString()));

    public static final DeferredHolder<Item, EmperorItem> EMPEROR_ITEM =
            ITEMS.register(
                    "emperor",
                    key -> new EmperorItem(
                            new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<SoundEvent, SoundEvent> HOL_EMP =
            sound("hol_emp");
    public static final DeferredHolder<SoundEvent, SoundEvent> VOID =
            sound("void");
    public static final DeferredHolder<SoundEvent, SoundEvent> EMP_SHOT =
            sound("emp_shot");
    public static final DeferredHolder<SoundEvent, SoundEvent> EMP_SUMMON =
            sound("emp_summon");
    public static final DeferredHolder<SoundEvent, SoundEvent> EMP_UNSUMMON =
            sound("emp_unsummon");
    public static final OstSoundList EMPEROR_OST =
            new OstSoundList(id("emp_ost"), SOUND_EVENTS);

    public static final DeferredHolder<StandType, EmperorStandType>
            THE_EMPEROR = STAND_TYPES.register(
                    "the_emperor", EmperorAddon::createStand);

    public EmperorAddon(IEventBus modEventBus) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_VISUAL_CONTEXT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_RANDOM_WEIGHTS_V1)) {
            throw new IllegalStateException(
                    "RotP Emperor requires ROTP addon ABI 1 with "
                            + "namespaced ability resources and the "
                            + "Stand visual context and random weights.");
        }
        // the target abilities call Ability.checkPerformerStun (R148 core)
        if (!RotpAddonApi.supportsFeature(
                RotpAddonApi.FEATURE_ABILITY_PERFORMER_STUN_V1)) {
            throw new IllegalStateException(
                    "RotP Emperor requires the core performer stun "
                            + "check (a core from the same release).");
        }

        ABILITY_TYPES.register(modEventBus);
        SPECIAL_ACTIONS.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        ITEMS.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        ATTACHMENT_TYPES.register(modEventBus);
        modEventBus.addListener(EmperorAddon::registerPayloads);
        modEventBus.addListener(
                EmperorEvents::registerAttributes);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static EmperorModeState modeState(Player player) {
        return player.getData(EMPEROR_MODE_STATE);
    }

    public static boolean isEmperorUser(Player player) {
        StandPower power = StandPower.get(player);
        return power != null && power.getPowerType() == THE_EMPEROR.get();
    }

    public static EmperorStandEntity controller(Player player) {
        StandPower power = StandPower.get(player);
        return power != null
                && power.getPowerType() == THE_EMPEROR.get()
                && power.getSummonedStandEntity()
                        instanceof EmperorStandEntity controller
                ? controller
                : null;
    }

    public static void syncMode(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(
                    serverPlayer,
                    new EmperorModeSyncPayload(
                            modeState(player).mode().ordinal()));
        }
    }

    private static void registerPayloads(
            RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(
                EmperorModeSyncPayload.TYPE,
                EmperorModeSyncPayload.STREAM_CODEC,
                EmperorModeSyncPayload::handle);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(
            String name) {
        return SOUND_EVENTS.register(
                name, SoundEvent::createVariableRangeEvent);
    }

    private static EmperorStandType createStand(ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addAbility(TARGET_MODE_NAME, TARGET_MODE)
                .addAbility(SET_TARGET_NAME, SET_TARGET)
                .addAbility(CLEAR_TARGET_NAME, CLEAR_TARGET)
                .addAbility(GIVE_EMPEROR_NAME, GIVE_EMPEROR);

        moveset.makeControlScheme("hotbar")
                .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
                .addToHotbar(
                        TARGET_MODE_NAME, 0, InputMethod.CLICK)
                .addToHotbar(
                        SET_TARGET_NAME, 0, InputMethod.CLICK)
                .finalizeControlScheme();
        moveset.makeControlScheme("keybinds")
                .finalizeControlScheme();

        moveset
                .addSkill(StandUnlockableSkill.startingAbility(
                        TARGET_MODE_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        SET_TARGET_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        CLEAR_TARGET_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        GIVE_EMPEROR_NAME));

        return new EmperorStandType(
                new StandStats.Builder()
                        .power(POWER)
                        .speed(SPEED)
                        .range(EFFECTIVE_RANGE, MAX_RANGE)
                        .durability(DURABILITY)
                        .precision(PRECISION)
                        .randomWeight(RANDOM_WEIGHT)
                        .build(),
                moveset,
                EMPEROR_STAND_ENTITY.get(),
                id)
                .standControlType(StandControlType.PHENOMENON)
                .manualControl(false)
                .distanceStrengthDecay(false)
                .standDimensions(0.0F, 0.0F)
                .summonShout(() -> HOL_EMP)
                .summonSound(() -> EMP_SUMMON)
                .unsummonSound(() -> EMP_UNSUMMON)
                .ost(() -> EMPEROR_OST);
    }
}
