package rotp.addons.thelock;

import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

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
import rotp.core.util.sound.OstSoundList;

import rotp.addons.thelock.ability.CheckTargetAbility;
import rotp.addons.thelock.ability.RemoveLockAbility;
import rotp.addons.thelock.ability.SetLockAbility;
import rotp.addons.thelock.effect.LockEffect;
import rotp.addons.thelock.entity.TheLockStandEntity;
import rotp.addons.thelock.item.LockItemData;
import rotp.addons.thelock.item.TheLockItem;
import rotp.addons.thelock.network.LockStateSyncPayload;
import rotp.addons.thelock.stand.TheLockStandType;
import rotp.addons.thelock.state.LockState;
import rotp.core.init.ModStatusEffects;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@Mod(TheLockAddon.MOD_ID)
public final class TheLockAddon {
    public static final String MOD_ID = "rotp_tl";
    public static final String SET_LOCK_NAME = "the_lock_set_lock";
    public static final String REMOVE_LOCK_NAME = "the_lock_remove_lock";
    public static final String CHECK_TARGET_NAME = "the_lock_check_target";
    public static final String NETWORK_PROTOCOL_VERSION = "2";

    public static final double POWER = 2.0D;
    public static final double SPEED = 2.0D;
    public static final double EFFECTIVE_RANGE = 30.0D;
    public static final double MAX_RANGE = 30.0D;
    public static final double DURABILITY = 15.0D;
    public static final double PRECISION = 2.0D;

    private static final int REQUIRED_CORE_ABI = 1;

    public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES =
            DeferredRegister.create(JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, MOD_ID);
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<StandType> STAND_TYPES =
            DeferredRegister.create(JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);
    public static final DeferredRegister.DataComponents DATA_COMPONENT_TYPES =
            DeferredRegister.createDataComponents(
                    Registries.DATA_COMPONENT_TYPE, MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(
                    NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID);

    public static final Supplier<DataComponentType<LockItemData>>
            LOCK_ITEM_OWNER =
            DATA_COMPONENT_TYPES.registerComponentType(
                    "lock_item_owner",
                    builder -> builder
                            .persistent(LockItemData.CODEC)
                            .networkSynchronized(LockItemData.STREAM_CODEC)
                            .cacheEncoding());

    public static final Supplier<AttachmentType<LockState>> LOCK_STATE =
            ATTACHMENT_TYPES.register(
                    "lock_state",
                    () -> AttachmentType.serializable(
                                    holder -> holder instanceof LivingEntity
                                            ? new LockState()
                                            : null)
                            .build());

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<SetLockAbility>> SET_LOCK =
            ABILITY_TYPES.register(
                    SET_LOCK_NAME,
                    key -> new AbilityType<>(key, SetLockAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<RemoveLockAbility>> REMOVE_LOCK =
            ABILITY_TYPES.register(
                    REMOVE_LOCK_NAME,
                    key -> new AbilityType<>(key, RemoveLockAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<CheckTargetAbility>> CHECK_TARGET =
            ABILITY_TYPES.register(
                    CHECK_TARGET_NAME,
                    key -> new AbilityType<>(key, CheckTargetAbility::new));

    public static final DeferredHolder<
            EntityType<?>, EntityType<TheLockStandEntity>>
            THE_LOCK_ENTITY = ENTITY_TYPES.register(
                    "the_lock",
                    key -> EntityType.Builder.<TheLockStandEntity>of(
                                    TheLockStandEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.0F, 0.0F)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<Item, TheLockItem> THE_LOCK_ITEM =
            ITEMS.register(
                    "the_lock",
                    key -> new TheLockItem(
                            new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<MobEffect, LockEffect> LOCK_EFFECT =
            MOB_EFFECTS.register("lock_effect", LockEffect::new);

    public static final DeferredHolder<SoundEvent, SoundEvent>
            THE_LOCK_SUMMON_VOICELINE =
            SOUND_EVENTS.register(
                    "example_stand_summon_voiceline",
                    SoundEvent::createVariableRangeEvent);

    public static final OstSoundList THE_LOCK_OST =
            new OstSoundList(
                    id("example_stand_ost"), SOUND_EVENTS);

    public static final DeferredHolder<StandType, TheLockStandType>
            THE_LOCK =
            STAND_TYPES.register("the_lock", TheLockAddon::createStand);

    public TheLockAddon(IEventBus modEventBus) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_VISUAL_CONTEXT_V1)) {
            throw new IllegalStateException(
                    "RotP The Lock requires ROTP addon ABI 1 with "
                            + "namespaced ability resources and the "
                            + "Stand visual context.");
        }
        // CheckTargetAbility calls Ability.checkPerformerStun (R148 core)
        if (!RotpAddonApi.supportsFeature(
                RotpAddonApi.FEATURE_ABILITY_PERFORMER_STUN_V1)) {
            throw new IllegalStateException(
                    "RotP The Lock requires the core performer stun "
                            + "check (a core from the same release).");
        }

        ABILITY_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        ITEMS.register(modEventBus);
        MOB_EFFECTS.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        DATA_COMPONENT_TYPES.register(modEventBus);
        ATTACHMENT_TYPES.register(modEventBus);
        modEventBus.addListener(TheLockAddon::registerPayloads);
        modEventBus.addListener(TheLockEvents::registerAttributes);
        modEventBus.addListener(TheLockAddon::addCreativeTabItems);
        modEventBus.addListener(TheLockAddon::commonSetup);
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> ModStatusEffects.TRACKED_EFFECTS.add(LOCK_EFFECT));
    }

    /** 1.16 listed The Lock item in the ROTP main tab. */
    private static void addCreativeTabItems(
            BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == rotp.core.init.ModItems.MAIN_TAB.getKey()) {
            event.accept(THE_LOCK_ITEM.get());
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static LockState lockState(LivingEntity entity) {
        return entity.getData(LOCK_STATE);
    }

    public static boolean isTheLockUser(LivingEntity entity) {
        StandPower power = StandPower.get(entity);
        return power != null && power.getPowerType() == THE_LOCK.get();
    }

    public static TheLockStandEntity controller(LivingEntity entity) {
        StandPower power = StandPower.get(entity);
        return power != null
                && power.getPowerType() == THE_LOCK.get()
                && power.getSummonedStandEntity()
                        instanceof TheLockStandEntity controller
                ? controller
                : null;
    }

    public static void syncLockState(LivingEntity target) {
        if (target.level().isClientSide()) {
            return;
        }
        LockState state = lockState(target);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                target,
                LockStateSyncPayload.from(target, state));
    }

    public static void syncLockState(
            ServerPlayer player, LivingEntity target) {
        PacketDistributor.sendToPlayer(
                player,
                LockStateSyncPayload.from(
                        target, lockState(target)));
    }

    private static void registerPayloads(
            RegisterPayloadHandlersEvent event) {
        event.registrar(NETWORK_PROTOCOL_VERSION).playToClient(
                LockStateSyncPayload.TYPE,
                LockStateSyncPayload.STREAM_CODEC,
                LockStateSyncPayload::handle);
    }

    private static TheLockStandType createStand(ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addAbility(SET_LOCK_NAME, SET_LOCK)
                .addAbility(REMOVE_LOCK_NAME, REMOVE_LOCK)
                .addAbility(CHECK_TARGET_NAME, CHECK_TARGET);

        moveset.makeControlScheme("hotbar")
                .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
                .addToHotbar(SET_LOCK_NAME, 0, InputMethod.HOLD)
                .addToHotbar(REMOVE_LOCK_NAME, 0, InputMethod.CLICK)
                .addToHotbar(CHECK_TARGET_NAME, 0, InputMethod.CLICK)
                .finalizeControlScheme();
        moveset.makeControlScheme("keybinds")
                .finalizeControlScheme();

        moveset
                .addSkill(StandUnlockableSkill.startingAbility(
                        SET_LOCK_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        REMOVE_LOCK_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        CHECK_TARGET_NAME));

        TheLockStandType stand = new TheLockStandType(
                new StandStats.Builder()
                        .power(POWER)
                        .speed(SPEED)
                        .range(EFFECTIVE_RANGE, MAX_RANGE)
                        .durability(DURABILITY)
                        .precision(PRECISION)
                        .build(),
                moveset,
                THE_LOCK_ENTITY.get(),
                id);
        stand.standControlType(StandControlType.PHENOMENON);
        stand.manualControl(false);
        stand.distanceStrengthDecay(false);
        stand.summonShout(() -> THE_LOCK_SUMMON_VOICELINE);
        stand.ost(() -> THE_LOCK_OST);
        return stand.standDimensions(0.0F, 0.0F);
    }
}
