package rotp.addons.heavensdoor;

import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import java.util.function.Supplier;

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
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.util.sound.OstSoundList;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.impl.stands._entitybase.StandEntityBlockAbility;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;

import rotp.addons.heavensdoor.ability.OpenBookAbility;
import rotp.addons.heavensdoor.ability.HeavensDoorFinisherAbility;
import rotp.addons.heavensdoor.ability.HeavensDoorTurnIntoBookAbility;
import rotp.addons.heavensdoor.ability.RemoveBookAbility;
import rotp.addons.heavensdoor.ability.ReturnPagesAbility;
import rotp.addons.heavensdoor.ability.TearOutAPageAbility;
import rotp.addons.heavensdoor.effect.BookEffect;
import rotp.addons.heavensdoor.effect.HeavensDoorStatusEffect;
import rotp.addons.heavensdoor.effect.KysEffect;
import rotp.addons.heavensdoor.effect.CannotMoveEffect;
import rotp.addons.heavensdoor.entity.HeavensDoorStandEntity;
import rotp.addons.heavensdoor.item.PageData;
import rotp.addons.heavensdoor.item.PageItem;
import rotp.addons.heavensdoor.network.BookStateSyncPayload;
import rotp.addons.heavensdoor.network.BookCommandIntentPayload;
import rotp.addons.heavensdoor.network.OpenBookSnapshotPayload;
import rotp.addons.heavensdoor.state.BookState;
import rotp.addons.heavensdoor.state.BookCommandState;
import rotp.addons.heavensdoor.state.WrittenCommandState;
import rotp.addons.heavensdoor.state.BookCommandLeaseService;
import rotp.addons.heavensdoor.state.HeavensDoorCoreBindings;
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
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@Mod(HeavensDoorAddon.MOD_ID)
public final class HeavensDoorAddon {
    public static final String MOD_ID = "rotp_hd";

    public static final String TURN_INTO_BOOK_ID =
            "heavens_door_turn_into_a_book";
    public static final String BARRAGE_ID =
            "heavens_door_barrage";
    public static final String FINISHER_ID =
            "heavens_door_finisher_punch";
    public static final String BLOCK_ID =
            "heavens_door_block";
    public static final String OPEN_BOOK_ID =
            "heavens_door_open_book";
    public static final String REMOVE_BOOK_ID =
            "heavens_door_remove_book";
    public static final String TEAR_PAGE_ID =
            "heavens_door_tear_out_a_page";
    public static final String RETURN_PAGES_ID =
            "heavens_door_return_pages";

    public static final double POWER = 6.0D;
    public static final double SPEED = 12.0D;
    public static final double EFFECTIVE_RANGE = 2.0D;
    public static final double MAX_RANGE = 12.0D;
    public static final double DURABILITY = 12.0D;
    public static final double PRECISION = 9.0D;
    public static final float STAND_WIDTH = 0.4F;
    public static final float STAND_HEIGHT = 1.25F;

    private static final int REQUIRED_CORE_ABI = 1;
    public static final String NETWORK_PROTOCOL_VERSION = "6";

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
    public static final DeferredRegister.DataComponents
            DATA_COMPONENT_TYPES =
            DeferredRegister.createDataComponents(
                    Registries.DATA_COMPONENT_TYPE, MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(
                    NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID);

    public static final Supplier<AttachmentType<BookState>> BOOK_STATE =
            ATTACHMENT_TYPES.register(
                    "book_state",
                    () -> AttachmentType.serializable(
                                    holder -> holder instanceof LivingEntity
                                            ? new BookState()
                                            : null)
                            .build());

    public static final Supplier<AttachmentType<BookCommandState>>
            BOOK_COMMAND_STATE = ATTACHMENT_TYPES.register(
                    "book_command_state",
                    () -> AttachmentType.serializable(
                                    holder -> holder instanceof LivingEntity
                                            ? new BookCommandState()
                                            : null)
                            .build());

    public static final Supplier<AttachmentType<WrittenCommandState>> WRITTEN_COMMAND_STATE =
            ATTACHMENT_TYPES.register("written_command_state", () -> AttachmentType.serializable(
                    holder -> holder instanceof LivingEntity ? new WrittenCommandState() : null).build());

    public static final Supplier<DataComponentType<PageData>> PAGE_DATA =
            DATA_COMPONENT_TYPES.registerComponentType(
                    "page_data",
                    builder -> builder
                            .persistent(PageData.CODEC)
                            .networkSynchronized(PageData.STREAM_CODEC)
                            .cacheEncoding());

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<HeavensDoorTurnIntoBookAbility>>
            TURN_INTO_BOOK = ABILITY_TYPES.register(
                    TURN_INTO_BOOK_ID,
                    key -> new AbilityType<>(
                            key, HeavensDoorTurnIntoBookAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<StandEntityBarrageAbility>>
            HEAVENS_DOOR_BARRAGE = ABILITY_TYPES.register(
                    BARRAGE_ID,
                    key -> new AbilityType<>(
                            key, StandEntityBarrageAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<HeavensDoorFinisherAbility>>
            HEAVENS_DOOR_FINISHER = ABILITY_TYPES.register(
                    FINISHER_ID,
                    key -> new AbilityType<>(
                            key, HeavensDoorFinisherAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<StandEntityBlockAbility>>
            HEAVENS_DOOR_BLOCK = ABILITY_TYPES.register(
                    BLOCK_ID,
                    key -> new AbilityType<>(
                            key, StandEntityBlockAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<OpenBookAbility>>
            OPEN_BOOK = ABILITY_TYPES.register(
                    OPEN_BOOK_ID,
                    key -> new AbilityType<>(key, OpenBookAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<RemoveBookAbility>>
            REMOVE_BOOK = ABILITY_TYPES.register(
                    REMOVE_BOOK_ID,
                    key -> new AbilityType<>(key, RemoveBookAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<TearOutAPageAbility>>
            TEAR_OUT_A_PAGE = ABILITY_TYPES.register(
                    TEAR_PAGE_ID,
                    key -> new AbilityType<>(
                            key, TearOutAPageAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<ReturnPagesAbility>>
            RETURN_PAGES = ABILITY_TYPES.register(
                    RETURN_PAGES_ID,
                    key -> new AbilityType<>(key, ReturnPagesAbility::new));

    public static final DeferredHolder<
            EntityType<?>, EntityType<HeavensDoorStandEntity>>
            HEAVENS_DOOR_ENTITY = ENTITY_TYPES.register(
                    "heavens_door",
                    key -> EntityType.Builder
                            .<HeavensDoorStandEntity>of(
                                    HeavensDoorStandEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(STAND_WIDTH, STAND_HEIGHT)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<Item, PageItem> PAGE_ITEM =
            ITEMS.register(
                    "page_item",
                    key -> new PageItem(
                            new Item.Properties()
                                    .stacksTo(32)
                                    .rarity(Rarity.RARE)));

    public static final DeferredHolder<MobEffect, BookEffect>
            BOOK_EFFECT = MOB_EFFECTS.register(
                    "book_effect", BookEffect::new);
    public static final DeferredHolder<
            MobEffect, HeavensDoorStatusEffect>
            BLINDNESS_ENTITY_EFFECT = marker(
                    "blindness_entity_effect", 273);
    public static final DeferredHolder<MobEffect, KysEffect>
            KYS_EFFECT = MOB_EFFECTS.register(
                    "kys_effect", KysEffect::new);
    public static final DeferredHolder<MobEffect, CannotMoveEffect> CANNOT_MOVE =
            MOB_EFFECTS.register("cannot_move", CannotMoveEffect::new);
    public static final DeferredHolder<
            MobEffect, HeavensDoorStatusEffect>
            FORGOT_ATTACK = marker("forgot_attack", 0xF01111);
    public static final DeferredHolder<
            MobEffect, HeavensDoorStatusEffect>
            FORGOT_SECOND_HAND = marker(
                    "forgot_second_hand", 0xFF0000);
    public static final DeferredHolder<
            MobEffect, HeavensDoorStatusEffect>
            FORGOT_SWIM = marker("forgot_swim", 0xFF0500);
    public static final DeferredHolder<
            MobEffect, HeavensDoorStatusEffect>
            FORGOT_RUN = marker("forgot_run", 0xFF0500);
    public static final DeferredHolder<
            MobEffect, HeavensDoorStatusEffect>
            FORGOT_JUMP = marker("forgot_jump", 0xFF0500);
    public static final DeferredHolder<
            MobEffect, HeavensDoorStatusEffect>
            FORGOT_BREAK = marker("forgot_break", 0xFF0500);
    public static final DeferredHolder<
            MobEffect, HeavensDoorStatusEffect>
            FORGOT_CRAFT = marker("forgot_craft", 0xFF0500);
    public static final DeferredHolder<
            MobEffect, HeavensDoorStatusEffect>
            FORGOT_STAND = marker("forgot_stand", 0xFF0500);
    public static final DeferredHolder<
            MobEffect, HeavensDoorStatusEffect>
            FORGOT_CONTAINERS = marker(
                    "forgot_containers", 0xFF0500);
    public static final DeferredHolder<
            MobEffect, HeavensDoorStatusEffect>
            FORGOT_SHIFT = marker("forgot_shift", 0xFF0500);

    public static final DeferredHolder<SoundEvent, SoundEvent>
            ROHAN_SUMMON = sound("rohan_summon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            ROHAN_LIGHT_ATTACK = sound("rohan_light_attack");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            ROHAN_BARRAGE = sound("rohan_barrage");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            WRITE_COMMAND = sound("heavens_door_write_command");
    public static final OstSoundList HEAVENS_DOOR_OST =
            new OstSoundList(
                    id("heavens_door_ost"), SOUND_EVENTS);

    public static final DeferredHolder<StandType, EntityStandType>
            HEAVENS_DOOR = STAND_TYPES.register(
                    "heavens_door", HeavensDoorAddon::createStand);

    public HeavensDoorAddon(IEventBus modEventBus) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_VISUAL_CONTEXT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_FIRST_PERSON_POST_ARM_LAYERS_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_CONTROLLED_ENTITY_COMBAT_LEASES_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_CONTROLLED_MOB_BEHAVIOR_LEASES_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_SHOUTS_V1)) {
            throw new IllegalStateException(
                    "RotP Heaven's Door requires ROTP addon ABI 1 with "
                            + "namespaced ability resources and Stand "
                            + "visual contexts plus first-person post-arm "
                            + "layers and controlled-entity command "
                            + "leases.");
        }
        // Open/Remove Book call Ability.checkPerformerStun (R148 core)
        if (!RotpAddonApi.supportsFeature(
                RotpAddonApi.FEATURE_ABILITY_PERFORMER_STUN_V1)) {
            throw new IllegalStateException(
                    "RotP Heaven's Door requires the core performer "
                            + "stun check (a core from the same release).");
        }

        HeavensDoorCoreBindings.initialize();
        BookCommandLeaseService.initialize();
        ABILITY_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        ITEMS.register(modEventBus);
        MOB_EFFECTS.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        DATA_COMPONENT_TYPES.register(modEventBus);
        ATTACHMENT_TYPES.register(modEventBus);
        modEventBus.addListener(HeavensDoorEvents::registerAttributes);
        modEventBus.addListener(HeavensDoorEvents::addMobAttackDamage);
        modEventBus.addListener(HeavensDoorAddon::registerPayloads);
        modEventBus.addListener(HeavensDoorAddon::addCreativeTabItems);
    }

    /** 1.16 listed the page item in the ROTP main tab. */
    private static void addCreativeTabItems(
            BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == rotp.core.init.ModItems.MAIN_TAB.getKey()) {
            event.accept(PAGE_ITEM.get());
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static BookState bookState(LivingEntity entity) {
        return entity.getData(BOOK_STATE);
    }

    public static BookCommandState bookCommandState(
            LivingEntity entity) {
        return entity.getData(BOOK_COMMAND_STATE);
    }

    public static boolean isHeavensDoorUser(LivingEntity entity) {
        StandPower power = entity != null ? StandPower.get(entity) : null;
        return power != null
                && power.hasPower()
                && power.getPowerType() == HEAVENS_DOOR.get();
    }

    public static HeavensDoorStandEntity controller(
            LivingEntity entity) {
        StandPower power = entity != null ? StandPower.get(entity) : null;
        StandEntity summoned = power != null
                ? power.getSummonedStandEntity()
                : null;
        return power != null
                && power.hasPower()
                && power.isSummoned()
                && power.getPowerType() == HEAVENS_DOOR.get()
                && summoned instanceof HeavensDoorStandEntity stand
                ? stand
                : null;
    }

    public static void syncBookState(LivingEntity target) {
        if (target.level().isClientSide()) {
            return;
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                target,
                BookStateSyncPayload.from(target, bookState(target)));
    }

    public static void syncBookState(
            ServerPlayer player, LivingEntity target) {
        PacketDistributor.sendToPlayer(
                player,
                BookStateSyncPayload.from(target, bookState(target)));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(
            String path) {
        return SOUND_EVENTS.register(
                path, SoundEvent::createVariableRangeEvent);
    }

    private static DeferredHolder<
            MobEffect, HeavensDoorStatusEffect> marker(
            String path, int color) {
        return MOB_EFFECTS.register(
                path, () -> new HeavensDoorStatusEffect(color));
    }

    private static void registerPayloads(
            RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar =
                event.registrar(NETWORK_PROTOCOL_VERSION);
        registrar.playToClient(
                BookStateSyncPayload.TYPE,
                BookStateSyncPayload.STREAM_CODEC,
                BookStateSyncPayload::handle);
        registrar.playToClient(
                OpenBookSnapshotPayload.TYPE,
                OpenBookSnapshotPayload.STREAM_CODEC,
                OpenBookSnapshotPayload::handle);
        registrar.playToServer(
                BookCommandIntentPayload.TYPE,
                BookCommandIntentPayload.STREAM_CODEC,
                BookCommandIntentPayload::handle);
    }

    private static EntityStandType createStand(ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addHumanoidStandStuff()
                .addAbility("punch", TURN_INTO_BOOK)
                .addAbility(
                        "barrage",
                        HEAVENS_DOOR_BARRAGE,
                        barrage -> barrage.barrageCrySound(
                                ROHAN_BARRAGE))
                // 1.16 InitStands :65: remove_book was HD's only heavy
                // attack (0 damage, ARMS) with the finisher as its variation.
                .addAbility(REMOVE_BOOK_ID, REMOVE_BOOK)
                .addAbility(
                        "finisher_punch",
                        HEAVENS_DOOR_FINISHER,
                        punch -> {
                            punch.heavyPunchImpactSound(WRITE_COMMAND);
                            punch.initIsFinisher(REMOVE_BOOK_ID);
                            punch.resolveLevelToUnlock(3);
                        })
                .addAbility("guard", HEAVENS_DOOR_BLOCK)
                .addAbility(OPEN_BOOK_ID, OPEN_BOOK)
                .addAbility(TEAR_PAGE_ID, TEAR_OUT_A_PAGE)
                .addAbility(RETURN_PAGES_ID, RETURN_PAGES)
                .makeControlScheme("hotbar")
                    .bind("punch", InputMethod.CLICK, InputKey.LMB)
                    .bind("barrage", InputMethod.HOLD, InputKey.LMB)
                    .bind(REMOVE_BOOK_ID, InputMethod.CLICK, InputKey.RMB)
                    .bind("guard", InputMethod.HOLD, InputKey.RMB)
                    .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
                    .addToHotbar(
                            OPEN_BOOK_ID, 0, InputMethod.CLICK)
                    .addToHotbar(
                            TEAR_PAGE_ID, 0, InputMethod.HOLD)
                    .addHotbarSlotVariation(
                            RETURN_PAGES_ID,
                            TEAR_PAGE_ID,
                            InputKey.Modifier.SHIFT,
                            InputMethod.CLICK)
                .finalizeControlScheme()
                .makeControlScheme("keybinds")
                    .bind("punch", InputMethod.CLICK, InputKey.LMB)
                    .bind("barrage", InputMethod.HOLD, InputKey.LMB)
                    .bind(
                            REMOVE_BOOK_ID,
                            InputMethod.CLICK,
                            InputKey.RMB)
                    .bind("guard", InputMethod.HOLD, InputKey.RMB)
                .finalizeControlScheme()
                .addSkill(StandUnlockableSkill.startingAbility("punch"))
                .addSkill(StandUnlockableSkill.startingAbility("barrage"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        REMOVE_BOOK_ID))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "finisher_punch"))
                .addSkill(StandUnlockableSkill.startingAbility("guard"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        OPEN_BOOK_ID))
                .addSkill(StandUnlockableSkill.startingAbility(
                        TEAR_PAGE_ID))
                .addSkill(StandUnlockableSkill.startingAbility(
                        RETURN_PAGES_ID))
                .addHumanoidStandSkills();

        return new EntityStandType(
                new StandStats.Builder()
                        .power(POWER)
                        .speed(SPEED)
                        .range(EFFECTIVE_RANGE, MAX_RANGE)
                        .durability(DURABILITY)
                        .precision(PRECISION)
                        .build(),
                moveset,
                HEAVENS_DOOR_ENTITY.get(),
                id)
                .standControlType(StandControlType.CLOSE_RANGE_DIRECT)
                .manualControl(true)
                .distanceStrengthDecay(true)
                .standDimensions(STAND_WIDTH, STAND_HEIGHT)
                .summonShout(() -> ROHAN_SUMMON)
                .summonSound(() -> ModSoundEvents.STAND_SUMMON)
                .unsummonSound(() -> ModSoundEvents.STAND_UNSUMMON)
                .ost(() -> HEAVENS_DOOR_OST);
    }
}
