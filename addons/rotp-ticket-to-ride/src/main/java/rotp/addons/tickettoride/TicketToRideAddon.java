package rotp.addons.tickettoride;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import java.util.List;
import java.util.function.Supplier;

import rotp.core.api.RotpAddonApi;
import rotp.core.core.JojoRegistries;
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

import rotp.addons.tickettoride.ability.RefillAmmoAbility;
import rotp.addons.tickettoride.ability.GiveTearsCompatibilityAbility;
import rotp.addons.tickettoride.ability.TicketToRideAbility;
import rotp.addons.tickettoride.ability.TicketToRideAbility.Mode;
import rotp.addons.tickettoride.effect.TicketToRideMobEffect;
import rotp.addons.tickettoride.entity.DeadPhotoMementoEntity;
import rotp.addons.tickettoride.entity.TicketToRideStandEntity;
import rotp.addons.tickettoride.item.TearsBladeData;
import rotp.addons.tickettoride.item.TearsBladeItem;
import rotp.addons.tickettoride.network.TearsWoundStateSyncPayload;
import rotp.addons.tickettoride.stand.TicketToRideStandType;
import rotp.addons.tickettoride.state.LoveTrainState;
import rotp.addons.tickettoride.state.TearsWoundState;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
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

@Mod(TicketToRideAddon.MOD_ID)
public final class TicketToRideAddon {
    public static final String MOD_ID = "rotp_ttr";

    public static final String EXPLOD = "explod";
    public static final String TRACE = "emiya";
    public static final String CRASH = "crash";
    public static final String RANDOM_MOB = "randommob";
    public static final String LIGHTNING_SINGLE =
            "lighting_single";
    public static final String LIGHTNING = "lighting";
    public static final String METEOR = "meteor";
    public static final String ITEM_FILL_NAME = "item_fill";
    public static final String GIVE_TEARS_NAME = "givetears";
    public static final String LOVE_TRAIN_NAME = "lovetrain";
    public static final String SHARE_LOVE_TRAIN =
            "sharelovetrain";
    public static final String REMOVE_SHARED = "remove";
    public static final String REMOVE_LOVE_TRAIN = "removel";

    public static final double POWER = 1.0D;
    public static final double SPEED = 1.0D;
    public static final double EFFECTIVE_RANGE = 0.0D;
    public static final double MAX_RANGE = 0.0D;
    public static final double DURABILITY = 10.0D;
    public static final double PRECISION = 1.0D;

    private static final int REQUIRED_CORE_ABI = 1;

    public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES =
            DeferredRegister.create(
                    JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(
                    Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, MOD_ID);
    public static final DeferredRegister<StandType> STAND_TYPES =
            DeferredRegister.create(
                    JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(
                    Registries.MOB_EFFECT, MOD_ID);
    public static final DeferredRegister<ParticleType<?>>
            PARTICLE_TYPES = DeferredRegister.create(
                    Registries.PARTICLE_TYPE, MOD_ID);
    public static final DeferredRegister<SoundEvent>
            SOUND_EVENTS = DeferredRegister.create(
                    Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister.DataComponents
            DATA_COMPONENT_TYPES =
            DeferredRegister.createDataComponents(
                    Registries.DATA_COMPONENT_TYPE, MOD_ID);
    public static final DeferredRegister<AttachmentType<?>>
            ATTACHMENT_TYPES = DeferredRegister.create(
                    NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID);

    public static final Supplier<DataComponentType<TearsBladeData>>
            TEARS_BLADE_DATA =
            DATA_COMPONENT_TYPES.registerComponentType(
                    "tears_blade_data",
                    builder -> builder
                            .persistent(TearsBladeData.CODEC)
                            .networkSynchronized(
                                    TearsBladeData.STREAM_CODEC)
                            .cacheEncoding());

    public static final Supplier<AttachmentType<TearsWoundState>>
            TEARS_WOUND_STATE =
            ATTACHMENT_TYPES.register(
                    "tears_wound_state",
                    () -> AttachmentType.serializable(
                                    holder -> holder
                                            instanceof LivingEntity
                                            ? new TearsWoundState()
                                            : null)
                            .build());

    public static final Supplier<AttachmentType<LoveTrainState>>
            LOVE_TRAIN_STATE =
            ATTACHMENT_TYPES.register(
                    "love_train_state",
                    () -> AttachmentType.serializable(
                                    holder -> holder
                                            instanceof LivingEntity
                                            ? new LoveTrainState()
                                            : null)
                            .build());

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<TicketToRideAbility>>
            EXPLOD_ABILITY = ability(EXPLOD, Mode.EXPLOD);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<TicketToRideAbility>>
            TRACE_ABILITY = ability(TRACE, Mode.TRACE);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<TicketToRideAbility>>
            CRASH_ABILITY = ability(CRASH, Mode.CRASH);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<TicketToRideAbility>>
            RANDOM_MOB_ABILITY =
            ability(RANDOM_MOB, Mode.RANDOM_MOB);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<TicketToRideAbility>>
            LIGHTNING_SINGLE_ABILITY =
            ability(LIGHTNING_SINGLE, Mode.LIGHTNING_SINGLE);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<TicketToRideAbility>>
            LIGHTNING_ABILITY =
            ability(LIGHTNING, Mode.LIGHTNING);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<TicketToRideAbility>>
            METEOR_ABILITY = ability(METEOR, Mode.METEOR);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<RefillAmmoAbility>>
            ITEM_FILL = ABILITY_TYPES.register(
                    ITEM_FILL_NAME,
                    key -> new AbilityType<>(
                            key, RefillAmmoAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<GiveTearsCompatibilityAbility>>
            GIVE_TEARS = ABILITY_TYPES.register(
                    GIVE_TEARS_NAME,
                    key -> new AbilityType<>(
                            key,
                            GiveTearsCompatibilityAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<TicketToRideAbility>>
            LOVE_TRAIN_ABILITY =
            ability(LOVE_TRAIN_NAME, Mode.LOVE_TRAIN);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<TicketToRideAbility>>
            SHARE_LOVE_TRAIN_ABILITY =
            ability(
                    SHARE_LOVE_TRAIN,
                    Mode.SHARE_LOVE_TRAIN);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<TicketToRideAbility>>
            REMOVE_SHARED_ABILITY =
            ability(REMOVE_SHARED, Mode.REMOVE_SHARED);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<TicketToRideAbility>>
            REMOVE_LOVE_TRAIN_ABILITY =
            ability(
                    REMOVE_LOVE_TRAIN,
                    Mode.REMOVE_LOVE_TRAIN);

    // 1.16 InitStatusEffect: TEARS and the passenger effect are both
    // TearsEffects(NEUTRAL, 4611416); LoveTrainEffect is BENEFICIAL, 16766720.
    public static final DeferredHolder<
            MobEffect, TicketToRideMobEffect> TEARS =
            MOB_EFFECTS.register(
                    "tears",
                    () -> new TicketToRideMobEffect(
                            MobEffectCategory.NEUTRAL,
                            0x465D58));
    public static final DeferredHolder<
            MobEffect, TicketToRideMobEffect> LOVE_TRAIN =
            MOB_EFFECTS.register(
                    "love_train",
                    () -> new TicketToRideMobEffect(
                            MobEffectCategory.BENEFICIAL,
                            0xFFD700));
    public static final DeferredHolder<
            MobEffect, TicketToRideMobEffect>
            SHARED_LOVE_TRAIN =
            MOB_EFFECTS.register(
                    "lovetrainss",
                    () -> new TicketToRideMobEffect(
                            MobEffectCategory.NEUTRAL,
                            0x465D58));

    public static final DeferredHolder<
            ParticleType<?>, SimpleParticleType>
            LOVE_TRAIN_LIGHT_WALL =
            particle("love_train_light_wall");
    public static final DeferredHolder<
            ParticleType<?>, SimpleParticleType>
            LOVE_TRAIN_LIGHT =
            particle("love_train_light");
    public static final DeferredHolder<
            ParticleType<?>, SimpleParticleType>
            LOVE_TRAIN_LIGHT_PILLAR =
            particle("love_train_light_pillar");
    public static final DeferredHolder<
            ParticleType<?>, SimpleParticleType>
            LOVE_TRAIN_LIGHT_SHORT =
            particle("love_train_light_short");
    public static final DeferredHolder<
            ParticleType<?>, SimpleParticleType>
            LOVE_TRAIN_LIGHT_BALL =
            particle("love_train_light_ball");

    public static final DeferredHolder<
            EntityType<?>, EntityType<TicketToRideStandEntity>>
            TICKET_TO_RIDE_ENTITY =
            ENTITY_TYPES.register(
                    "ticket_to_ride",
                    key -> EntityType.Builder
                            .<TicketToRideStandEntity>of(
                                    TicketToRideStandEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.0F, 0.0F)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<
            EntityType<?>, EntityType<DeadPhotoMementoEntity>>
            DEAD_PHOTO_MEMENTO =
            ENTITY_TYPES.register(
                    "dpm",
                    key -> EntityType.Builder
                            .<DeadPhotoMementoEntity>of(
                                    DeadPhotoMementoEntity::new,
                                    MobCategory.MISC)
                            // 1.16 InitEntities "dpm": 1 x 1, update every 2
                            .sized(1.0F, 1.0F)
                            .clientTrackingRange(64)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<Item, TearsBladeItem>
            TEARS_BLADE = ITEMS.register(
                    "tearsblade",
                    key -> new TearsBladeItem(
                            new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<SoundEvent, SoundEvent>
            TICKET_TO_RIDE_SUMMON_VOICE =
            SOUND_EVENTS.register(
                    "example_stand_summon_voiceline",
                    key -> SoundEvent.createVariableRangeEvent(
                            id("tand_summon_voiceline")));
    public static final OstSoundList TICKET_TO_RIDE_OST =
            new OstSoundList(id("stand_ost"), SOUND_EVENTS);

    public static final DeferredHolder<
            StandType, TicketToRideStandType>
            TICKET_TO_RIDE = STAND_TYPES.register(
                    "ticket_to_ride",
                    TicketToRideAddon::createStand);

    public TicketToRideAddon(IEventBus modEventBus) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                // canFireReleasedHold (R146 core held-action rechecks)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ENTITY_ACTION_HOOKS_V1)) {
            throw new IllegalStateException(
                    "RotP Ticket to Ride requires ROTP addon ABI 1 "
                            + "with namespaced ability resources and "
                            + "entity action hooks.");
        }
        // TicketToRideEvents reads the integrated_stand effect (R148 core)
        if (!RotpAddonApi.supportsFeature(
                RotpAddonApi.FEATURE_INTEGRATED_STAND_V1)) {
            throw new IllegalStateException(
                    "RotP Ticket to Ride requires the core integrated "
                            + "Stand effect (a core from the same "
                            + "release).");
        }

        ABILITY_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        ITEMS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        MOB_EFFECTS.register(modEventBus);
        PARTICLE_TYPES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        DATA_COMPONENT_TYPES.register(modEventBus);
        ATTACHMENT_TYPES.register(modEventBus);
        modEventBus.addListener(
                TicketToRideAddon::registerPayloads);
        modEventBus.addListener(
                TicketToRideEvents::registerAttributes);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                MOD_ID, path);
    }

    public static TearsWoundState woundState(
            LivingEntity entity) {
        return entity.getData(TEARS_WOUND_STATE);
    }

    public static LoveTrainState loveTrainState(
            LivingEntity entity) {
        return entity.getData(LOVE_TRAIN_STATE);
    }

    public static boolean isTicketToRideUser(
            LivingEntity entity) {
        StandPower power = StandPower.get(entity);
        return power != null
                && power.getPowerType() == TICKET_TO_RIDE.get();
    }

    public static TicketToRideStandEntity controller(
            LivingEntity entity) {
        StandPower power = StandPower.get(entity);
        return power != null
                && power.getPowerType() == TICKET_TO_RIDE.get()
                && power.isSummoned()
                && power.getSummonedStandEntity()
                        instanceof TicketToRideStandEntity controller
                ? controller
                : null;
    }

    public static void syncWoundState(LivingEntity target) {
        if (target.level().isClientSide()) {
            return;
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                target,
                TearsWoundStateSyncPayload.from(
                        target, woundState(target)));
    }

    public static void syncWoundState(
            ServerPlayer player, LivingEntity target) {
        PacketDistributor.sendToPlayer(
                player,
                TearsWoundStateSyncPayload.from(
                        target, woundState(target)));
    }

    private static DeferredHolder<
            AbilityType<?>, AbilityType<TicketToRideAbility>>
            ability(String name, Mode mode) {
        return ABILITY_TYPES.register(
                name,
                key -> new AbilityType<>(
                        key,
                        (type, id) -> new TicketToRideAbility(
                                type, id, mode)));
    }

    private static DeferredHolder<
            ParticleType<?>, SimpleParticleType>
            particle(String name) {
        return PARTICLE_TYPES.register(
                name, () -> new SimpleParticleType(false));
    }

    private static void registerPayloads(
            RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(
                TearsWoundStateSyncPayload.TYPE,
                TearsWoundStateSyncPayload.STREAM_CODEC,
                TearsWoundStateSyncPayload::handle);
    }

    private static TicketToRideStandType createStand(
            ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addAbility(EXPLOD, EXPLOD_ABILITY)
                .addAbility(TRACE, TRACE_ABILITY)
                .addAbility(CRASH, CRASH_ABILITY)
                .addAbility(RANDOM_MOB, RANDOM_MOB_ABILITY)
                .addAbility(
                        LIGHTNING_SINGLE,
                        LIGHTNING_SINGLE_ABILITY)
                .addAbility(LIGHTNING, LIGHTNING_ABILITY)
                .addAbility(METEOR, METEOR_ABILITY)
                .addAbility(ITEM_FILL_NAME, ITEM_FILL)
                .addAbility(GIVE_TEARS_NAME, GIVE_TEARS)
                .addAbility(
                        LOVE_TRAIN_NAME, LOVE_TRAIN_ABILITY)
                .addAbility(
                        SHARE_LOVE_TRAIN,
                        SHARE_LOVE_TRAIN_ABILITY)
                .addAbility(
                        REMOVE_SHARED,
                        REMOVE_SHARED_ABILITY)
                .addAbility(
                        REMOVE_LOVE_TRAIN,
                        REMOVE_LOVE_TRAIN_ABILITY);

        moveset.makeControlScheme("hotbar")
                .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
                .addToHotbar(
                        LIGHTNING_SINGLE, 0, InputMethod.HOLD)
                .addHotbarSlotVariation(
                        LIGHTNING,
                        LIGHTNING_SINGLE,
                        InputKey.Modifier.SHIFT,
                        InputMethod.HOLD)
                .addToHotbar(EXPLOD, 0, InputMethod.HOLD)
                .addToHotbar(METEOR, 0, InputMethod.HOLD)
                .addToHotbar(
                        ITEM_FILL_NAME, 0, InputMethod.HOLD)
                .addToHotbar(CRASH, 0, InputMethod.HOLD)
                .addHotbarSlotVariation(
                        RANDOM_MOB,
                        CRASH,
                        InputKey.Modifier.SHIFT,
                        InputMethod.HOLD)
                .addToHotbar(TRACE, 0, InputMethod.HOLD)
                .addToHotbar(
                        LOVE_TRAIN_NAME, 0, InputMethod.HOLD)
                .addHotbarSlotVariation(
                        REMOVE_LOVE_TRAIN,
                        LOVE_TRAIN_NAME,
                        InputKey.Modifier.SHIFT,
                        InputMethod.CLICK)
                .addToHotbar(
                        SHARE_LOVE_TRAIN,
                        0,
                        InputMethod.CLICK)
                .addHotbarSlotVariation(
                        REMOVE_SHARED,
                        SHARE_LOVE_TRAIN,
                        InputKey.Modifier.SHIFT,
                        InputMethod.CLICK)
                .finalizeControlScheme();
        moveset.makeControlScheme("keybinds")
                .finalizeControlScheme();

        for (String ability : List.of(
                EXPLOD,
                TRACE,
                CRASH,
                RANDOM_MOB,
                LIGHTNING_SINGLE,
                LIGHTNING,
                METEOR,
                ITEM_FILL_NAME,
                GIVE_TEARS_NAME,
                LOVE_TRAIN_NAME,
                SHARE_LOVE_TRAIN,
                REMOVE_SHARED,
                REMOVE_LOVE_TRAIN)) {
            moveset.addSkill(
                    StandUnlockableSkill.startingAbility(
                            ability));
        }

        return new TicketToRideStandType(
                new StandStats.Builder()
                        .power(POWER)
                        .speed(SPEED)
                        .range(EFFECTIVE_RANGE, MAX_RANGE)
                        .durability(DURABILITY)
                        .precision(PRECISION)
                        .build(),
                moveset,
                TICKET_TO_RIDE_ENTITY.get(),
                id)
                .standControlType(StandControlType.PHENOMENON)
                .manualControl(false)
                .distanceStrengthDecay(false)
                .standDimensions(0.0F, 0.0F)
                .summonShout(() -> TICKET_TO_RIDE_SUMMON_VOICE)
                .ost(() -> TICKET_TO_RIDE_OST);
    }
}
