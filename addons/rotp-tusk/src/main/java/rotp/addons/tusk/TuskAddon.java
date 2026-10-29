package rotp.addons.tusk;

import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import rotp.core.api.RotpAddonApi;
import rotp.core.api.timestop.TimeStopEntityMovementAuthorizers;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.util.sound.OstSoundList;

import rotp.addons.tusk.entity.NailEntity;
import rotp.addons.tusk.entity.TuskEntity;
import rotp.addons.tusk.entity.WormholeArmEntity;
import rotp.addons.tusk.entity.WormholeEntity;
import rotp.addons.tusk.entity.WormholePortalEntity;
import rotp.addons.tusk.ability.TuskActSelectAbility;
import rotp.addons.tusk.ability.TuskAct4AwarenessAbility;
import rotp.addons.tusk.ability.TuskChargedNailShotAbility;
import rotp.addons.tusk.ability.TuskInfiniteRotationAbility;
import rotp.addons.tusk.ability.TuskNailScratchAbility;
import rotp.addons.tusk.ability.TuskNailShotAbility;
import rotp.addons.tusk.ability.TuskOpenSpaceAbility;
import rotp.addons.tusk.ability.TuskPortalAbilities;
import rotp.addons.tusk.ability.TuskUppercutAbility;
import rotp.addons.tusk.ability.TuskHeavyPunchAbility;
import rotp.addons.tusk.ability.TuskPunchAbility;
import rotp.addons.tusk.effect.InfiniteRotationEffect;
import rotp.addons.tusk.network.TuskPortalAcceleratePayload;
import rotp.addons.tusk.item.TeaItem;
import rotp.addons.tusk.network.TuskNailStateSyncPayload;
import rotp.addons.tusk.particle.NailSwipeParticleType;
import rotp.addons.tusk.stand.TuskStandType;
import rotp.addons.tusk.state.TuskInfiniteRotationState;
import rotp.addons.tusk.state.TuskNailState;
import rotp.addons.tusk.timestop.TuskAct4TimeStopMovementAuthorizer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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

import java.util.function.Supplier;

@Mod(TuskAddon.MOD_ID)
public final class TuskAddon {
    public static final String MOD_ID = "rotp_t";
    public static final int ACT_1 = 0;
    public static final int ACT_2 = 1;
    public static final int ACT_3 = 2;
    public static final int ACT_4 = 3;
    public static final float STAND_WIDTH = 0.175F;
    public static final float STAND_HEIGHT = 0.4875F;
    public static final double POWER = 3.0D;
    public static final double SPEED = 3.0D;
    public static final double EFFECTIVE_RANGE = 2.0D;
    public static final double MAX_RANGE = 2.0D;
    public static final double DURABILITY = 12.0D;
    public static final double PRECISION = 3.0D;
    public static final float ACT_2_STAND_WIDTH = 0.35F;
    public static final float ACT_2_STAND_HEIGHT = 0.975F;
    public static final double ACT_2_POWER = 5.0D;
    public static final double ACT_2_SPEED = 5.0D;
    public static final double ACT_2_EFFECTIVE_RANGE = 5.0D;
    public static final double ACT_2_MAX_RANGE = 7.0D;
    public static final double ACT_2_DURABILITY = 10.0D;
    public static final double ACT_2_PRECISION = 8.0D;
    public static final float ACT_3_STAND_WIDTH = 0.525F;
    public static final float ACT_3_STAND_HEIGHT = 1.4625F;
    public static final double ACT_3_POWER = 7.0D;
    public static final double ACT_3_SPEED = 7.0D;
    public static final double ACT_3_EFFECTIVE_RANGE = 5.0D;
    public static final double ACT_3_MAX_RANGE = 7.0D;
    public static final double ACT_3_DURABILITY = 7.0D;
    public static final double ACT_3_PRECISION = 10.0D;
    public static final float ACT_4_STAND_WIDTH = 0.7F;
    public static final float ACT_4_STAND_HEIGHT = 1.95F;
    public static final double ACT_4_POWER = 16.0D;
    public static final double ACT_4_SPEED = 14.0D;
    public static final double ACT_4_EFFECTIVE_RANGE = 8.0D;
    public static final double ACT_4_MAX_RANGE = 11.0D;
    public static final double ACT_4_DURABILITY = 16.0D;
    public static final double ACT_4_PRECISION = 13.0D;

    private static final int REQUIRED_CORE_ABI = 1;
    private static final String BLOCK_NAME = "tusk_block";
    private static final String TUSK_PUNCH_NAME = "tusk_punch";
    private static final String TUSK_BARRAGE_NAME = "tusk_barrage";
    private static final String TUSK_HEAVY_PUNCH_NAME =
            "tusk_heavy_punch";
    private static final String TUSK_UPPERCUT_NAME = "tusk_uppercut";
    public static final String INFINITE_ROTATION_NAME =
            "tusk_infinite_rotation";
    public static final String OPEN_SPACE_NAME = "tusk_open_space";
    private static final String ACT_4_AWARENESS_NAME =
            "tusk_act_4_time_stop_awareness";
    private static final String NAIL_SHOT_NAME = "tusk_nail_shot";
    private static final String NAIL_SCRATCH_NAME = "tusk_nail_scratch";
    private static final String CHARGED_NAIL_SHOT_NAME =
            "tusk_charged_nail_shot";
    private static final String SELECT_ACT_NAME = "tusk_select_act";
    private static final String WORMHOLE_NAME = "wormhole";
    private static final String MOVE_WORMHOLE_NAME = "move_wormhole";
    private static final String REMOVE_WORMHOLE_NAME =
            "remove_wormhole";
    private static final String WORMHOLE_ARM_NAME =
            "wormhole_with_arm";
    private static final String MOVE_WORMHOLE_ARM_NAME =
            "move_wormhole_with_arm";
    private static final String REMOVE_WORMHOLE_ARM_NAME =
            "remove_wormhole_with_arm";

    public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES =
            DeferredRegister.create(JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, MOD_ID);
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, MOD_ID);
    public static final DeferredRegister<StandType> STAND_TYPES =
            DeferredRegister.create(JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(
                    NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID);

    public static final Supplier<AttachmentType<TuskNailState>>
            TUSK_NAIL_STATE = ATTACHMENT_TYPES.register(
                    "tusk_nail_state",
                    () -> AttachmentType.serializable(
                                    holder -> holder instanceof Player
                                            ? new TuskNailState()
                                            : null)
                            .build());

    public static final Supplier<AttachmentType<TuskInfiniteRotationState>>
            TUSK_INFINITE_ROTATION_STATE = ATTACHMENT_TYPES.register(
                    "tusk_infinite_rotation_state",
                    () -> AttachmentType.serializable(
                                    holder -> holder
                                                    instanceof LivingEntity
                                            ? new TuskInfiniteRotationState()
                                            : null)
                            .build());

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<TuskNailShotAbility>> NAIL_SHOT =
            ABILITY_TYPES.register(
                    NAIL_SHOT_NAME,
                    key -> new AbilityType<>(
                            key, TuskNailShotAbility::new));

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<TuskNailScratchAbility>>
            NAIL_SCRATCH = ABILITY_TYPES.register(
                    NAIL_SCRATCH_NAME,
                    key -> new AbilityType<>(
                            key, TuskNailScratchAbility::new));

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<TuskChargedNailShotAbility>>
            CHARGED_NAIL_SHOT = ABILITY_TYPES.register(
                    CHARGED_NAIL_SHOT_NAME,
                    key -> new AbilityType<>(
                            key, TuskChargedNailShotAbility::new));

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<TuskActSelectAbility>>
            SELECT_ACT = ABILITY_TYPES.register(
                    SELECT_ACT_NAME,
                    key -> new AbilityType<>(
                            key, TuskActSelectAbility::new));

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<TuskAct4AwarenessAbility>>
            ACT_4_AWARENESS = ABILITY_TYPES.register(
                    ACT_4_AWARENESS_NAME,
                    key -> new AbilityType<>(
                            key, TuskAct4AwarenessAbility::new));

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<TuskUppercutAbility>>
            TUSK_UPPERCUT = ABILITY_TYPES.register(
                    TUSK_UPPERCUT_NAME,
                    key -> new AbilityType<>(
                            key, TuskUppercutAbility::new));

    // 1.16 Johnny shouts on the punch and heavy punch
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<TuskPunchAbility>>
            TUSK_PUNCH_TYPE = ABILITY_TYPES.register(
                    TUSK_PUNCH_NAME,
                    key -> new AbilityType<>(
                            key, TuskPunchAbility::new));

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<TuskHeavyPunchAbility>>
            TUSK_HEAVY_PUNCH_TYPE = ABILITY_TYPES.register(
                    TUSK_HEAVY_PUNCH_NAME,
                    key -> new AbilityType<>(
                            key, TuskHeavyPunchAbility::new));

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<TuskInfiniteRotationAbility>>
            TUSK_INFINITE_ROTATION = ABILITY_TYPES.register(
                    INFINITE_ROTATION_NAME,
                    key -> new AbilityType<>(
                            key, TuskInfiniteRotationAbility::new));

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<TuskOpenSpaceAbility>>
            TUSK_OPEN_SPACE = ABILITY_TYPES.register(
                    OPEN_SPACE_NAME,
                    key -> new AbilityType<>(
                            key, TuskOpenSpaceAbility::new));

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<TuskPortalAbilities.CreatePortal>>
            CREATE_WORMHOLE = ABILITY_TYPES.register(
                    WORMHOLE_NAME,
                    key -> new AbilityType<>(
                            key, TuskPortalAbilities.CreatePortal::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<TuskPortalAbilities.MovePortal>>
            MOVE_WORMHOLE = ABILITY_TYPES.register(
                    MOVE_WORMHOLE_NAME,
                    key -> new AbilityType<>(
                            key, TuskPortalAbilities.MovePortal::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<TuskPortalAbilities.RemovePortal>>
            REMOVE_WORMHOLE = ABILITY_TYPES.register(
                    REMOVE_WORMHOLE_NAME,
                    key -> new AbilityType<>(
                            key, TuskPortalAbilities.RemovePortal::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<TuskPortalAbilities.CreateArm>>
            CREATE_WORMHOLE_ARM = ABILITY_TYPES.register(
                    WORMHOLE_ARM_NAME,
                    key -> new AbilityType<>(
                            key, TuskPortalAbilities.CreateArm::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<TuskPortalAbilities.MoveArm>>
            MOVE_WORMHOLE_ARM = ABILITY_TYPES.register(
                    MOVE_WORMHOLE_ARM_NAME,
                    key -> new AbilityType<>(
                            key, TuskPortalAbilities.MoveArm::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<TuskPortalAbilities.RemoveArm>>
            REMOVE_WORMHOLE_ARM = ABILITY_TYPES.register(
                    REMOVE_WORMHOLE_ARM_NAME,
                    key -> new AbilityType<>(
                            key, TuskPortalAbilities.RemoveArm::new));

    public static final DeferredHolder<EntityType<?>, EntityType<TuskEntity>>
            TUSK_ENTITY = ENTITY_TYPES.register(
                    "tusk",
                    key -> EntityType.Builder.of(
                                    TuskEntity::new, MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(STAND_WIDTH, STAND_HEIGHT)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<EntityType<?>, EntityType<NailEntity>>
            NAIL_ENTITY = ENTITY_TYPES.register(
                    "nail",
                    key -> EntityType.Builder.<NailEntity>of(
                                    NailEntity::new, MobCategory.MISC)
                            .noSummon()
                            .sized(0.2F, 0.2F)
                            .clientTrackingRange(64)
                            .updateInterval(10)
                            .build(key.toString()));

    public static final DeferredHolder<
            EntityType<?>, EntityType<WormholeEntity>>
            WORMHOLE_ENTITY = ENTITY_TYPES.register(
                    "wormhole_damaging",
                    key -> EntityType.Builder.<WormholeEntity>of(
                                    WormholeEntity::new,
                                    MobCategory.MISC)
                            .noSummon()
                            .sized(0.6F, 0.1F)
                            .clientTrackingRange(64)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<
            EntityType<?>, EntityType<WormholePortalEntity>>
            WORMHOLE_PORTAL_ENTITY = ENTITY_TYPES.register(
                    "wormhole",
                    key -> EntityType.Builder
                            .<WormholePortalEntity>of(
                                    WormholePortalEntity::new,
                                    MobCategory.MISC)
                            .noSummon()
                            .sized(1.0F, 0.1F)
                            .clientTrackingRange(64)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<
            EntityType<?>, EntityType<WormholeArmEntity>>
            WORMHOLE_ARM_ENTITY = ENTITY_TYPES.register(
                    "wormhole_with_arm",
                    key -> EntityType.Builder.<WormholeArmEntity>of(
                                    WormholeArmEntity::new,
                                    MobCategory.MISC)
                            .noSummon()
                            .sized(1.0F, 0.1F)
                            .clientTrackingRange(64)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<Item, TeaItem> TEA_BOTTLE =
            ITEMS.register(
                    "tea_bottle",
                    key -> new TeaItem(new Item.Properties().stacksTo(16)));

    public static final DeferredHolder<
            MobEffect, InfiniteRotationEffect> INFINITE_ROTATION =
            EFFECTS.register(
                    "infinite_rotation",
                    key -> new InfiniteRotationEffect());

    public static final DeferredHolder<
            ParticleType<?>, NailSwipeParticleType>
            NAIL_SWIPE = PARTICLE_TYPES.register(
                    "nail_swipe", NailSwipeParticleType::new);

    public static final DeferredHolder<SoundEvent, SoundEvent>
            JOHNNY_ACT_1_SUMMON = sound("johnny_act_1_summon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            JOHNNY_ACT_2_SUMMON = sound("johnny_act_2_summon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            JOHNNY_ACT_3_SUMMON = sound("johnny_act_3_summon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            JOHNNY_ACT_4_SUMMON = sound("johnny_act_4_summon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            JOHNNY_BARRAGE = sound("johnny_ora");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            JOHNNY_LIGHT_ATTACK = sound("johnny_light_attack");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            JOHNNY_HEAVY_ATTACK = sound("johnny_heavy_attack");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            TUSK_PUNCH_LIGHT = sound("tusk_light_punch");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            TUSK_PUNCH_HEAVY = sound("tusk_heavy_punch");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            TUSK_SUMMON = sound("tusk_summon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            TUSK_UNSUMMON = sound("tusk_unsummon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            TUSK_CHIMIMI = sound("chimimi");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            NAIL_SHOT_SOUND = sound("nail_shot");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SCRATCH_SOUND = sound("scratch");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            WORMHOLE_OPEN = sound("wormhole_open");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            WORMHOLE_CLOSE = sound("wormhole_close");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            WORMHOLE_TELEPORT = sound("wormhole_teleport");
    public static final OstSoundList TUSK_OST =
            new OstSoundList(id("tusk_ost"), SOUND_EVENTS);

    public static final DeferredHolder<StandType, TuskStandType> TUSK =
            STAND_TYPES.register("tusk", TuskAddon::createAct1);
    public static final DeferredHolder<StandType, TuskStandType>
            TUSK_ACT_2 =
            STAND_TYPES.register("tusk_act_2", TuskAddon::createAct2);
    public static final DeferredHolder<StandType, TuskStandType>
            TUSK_ACT_3 =
            STAND_TYPES.register("tusk_act_3", TuskAddon::createAct3);
    public static final DeferredHolder<StandType, TuskStandType>
            TUSK_ACT_4 =
            STAND_TYPES.register("tusk_act_4", TuskAddon::createAct4);

    public TuskAddon(IEventBus modEventBus) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_VISUAL_CONTEXT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_POWER_TRANSITIONS_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_TIME_STOP_LIFECYCLE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_TIME_STOP_ENTITY_MOVEMENT_AUTHORIZERS_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_SHOUTS_V1)) {
            throw new IllegalStateException(
                    "RotP Tusk requires ROTP addon ABI 1 with namespaced "
                            + "ability resources, stand visual contexts, "
                            + "server-authoritative Stand transitions, "
                            + "the time-stop lifecycle, and entity movement "
                            + "authorizers.");
        }

        TimeStopEntityMovementAuthorizers.register(
                id(TuskAct4TimeStopMovementAuthorizer.REGISTRATION_PATH),
                TuskAct4TimeStopMovementAuthorizer::canMoveInStoppedTime);
        ABILITY_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        EFFECTS.register(modEventBus);
        ITEMS.register(modEventBus);
        PARTICLE_TYPES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        ATTACHMENT_TYPES.register(modEventBus);
        modEventBus.addListener(TuskAddon::registerPayloads);
        modEventBus.addListener(TuskAddon::addCreativeTabItems);
    }

    /** 1.16 listed the tea bottle in the ROTP main tab. */
    private static void addCreativeTabItems(
            BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == rotp.core.init.ModItems.MAIN_TAB.getKey()) {
            event.accept(TEA_BOTTLE.get());
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static TuskNailState nailState(Player player) {
        return player.getData(TUSK_NAIL_STATE);
    }

    public static TuskInfiniteRotationState infiniteRotationState(
            LivingEntity living) {
        return living.getData(TUSK_INFINITE_ROTATION_STATE);
    }

    public static boolean isTuskUser(Player player) {
        StandPower power = StandPower.get(player);
        return power != null && actIndex(power.getPowerType()) >= ACT_1;
    }

    public static boolean isAct2User(Player player) {
        StandPower power = StandPower.get(player);
        return power != null
                && actIndex(power.getPowerType()) == ACT_2;
    }

    public static boolean isAct3User(Player player) {
        StandPower power = StandPower.get(player);
        return power != null
                && actIndex(power.getPowerType()) == ACT_3;
    }

    public static boolean isAct4User(LivingEntity living) {
        StandPower power = StandPower.get(living);
        return power != null
                && actIndex(power.getPowerType()) == ACT_4;
    }

    public static boolean hasInfiniteRotationCharge(
            LivingEntity living) {
        return living instanceof Player player
                && isAct4User(player)
                && nailState(player).isInfiniteRotationCharged();
    }

    public static boolean isPortalActUser(Player player) {
        StandPower power = StandPower.get(player);
        int act = power != null
                ? actIndex(power.getPowerType())
                : -1;
        return act >= ACT_3 && act <= ACT_4;
    }

    public static boolean isChargedNailUser(Player player) {
        StandPower power = StandPower.get(player);
        int act = power != null
                ? actIndex(power.getPowerType())
                : -1;
        return act >= ACT_2 && act <= ACT_4;
    }

    public static int actIndex(StandType standType) {
        if (standType == TUSK.get()) {
            return ACT_1;
        }
        if (standType == TUSK_ACT_2.get()) {
            return ACT_2;
        }
        if (standType == TUSK_ACT_3.get()) {
            return ACT_3;
        }
        if (standType == TUSK_ACT_4.get()) {
            return ACT_4;
        }
        return -1;
    }

    public static int nailRegenTicks(Player player) {
        StandPower power = StandPower.get(player);
        int act = power != null ? actIndex(power.getPowerType()) : ACT_1;
        return (Math.clamp(act, ACT_1, ACT_4) + 1) * TuskNailState.REGEN_TICKS;
    }

    public static StandType standForAct(int actIndex) {
        return switch (actIndex) {
            case ACT_1 -> TUSK.get();
            case ACT_2 -> TUSK_ACT_2.get();
            case ACT_3 -> TUSK_ACT_3.get();
            case ACT_4 -> TUSK_ACT_4.get();
            default -> null;
        };
    }

    public static void syncNailState(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        TuskNailState state = nailState(player);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                serverPlayer,
                new TuskNailStateSyncPayload(
                        player.getId(),
                        state.nails(),
                        state.regenTicksRemaining(),
                        state.isInfiniteRotationCharged()));
    }

    private static void registerPayloads(
            RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("2");
        registrar.playToClient(
                TuskNailStateSyncPayload.TYPE,
                TuskNailStateSyncPayload.STREAM_CODEC,
                TuskNailStateSyncPayload::handle);
        registrar.playToServer(
                TuskPortalAcceleratePayload.TYPE,
                TuskPortalAcceleratePayload.STREAM_CODEC,
                TuskPortalAcceleratePayload::handle);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(
            String name) {
        return SOUND_EVENTS.register(
                name, SoundEvent::createVariableRangeEvent);
    }

    private static TuskStandType createAct1(ResourceLocation id) {
        TuskStandType stand = createStand(
                id,
                ACT_1,
                new StandStats.Builder()
                        .power(POWER)
                        .speed(SPEED)
                        .range(EFFECTIVE_RANGE, MAX_RANGE)
                        .durability(DURABILITY)
                        .precision(PRECISION)
                        .build(),
                STAND_WIDTH,
                STAND_HEIGHT,
                TuskEntity.ACT_1_FOLLOW_OFFSET,
                () -> JOHNNY_ACT_1_SUMMON);
        stand.standControlType(StandControlType.PHENOMENON);
        stand.manualControl(false);
        stand.distanceStrengthDecay(false);
        return stand;
    }

    private static TuskStandType createAct2(ResourceLocation id) {
        TuskStandType stand = createStand(
                id,
                ACT_2,
                new StandStats.Builder()
                        .power(ACT_2_POWER)
                        .speed(ACT_2_SPEED)
                        .range(
                                ACT_2_EFFECTIVE_RANGE,
                                ACT_2_MAX_RANGE)
                        .durability(ACT_2_DURABILITY)
                        .precision(ACT_2_PRECISION)
                        .build(),
                ACT_2_STAND_WIDTH,
                ACT_2_STAND_HEIGHT,
                TuskEntity.ACT_2_FOLLOW_OFFSET,
                () -> JOHNNY_ACT_2_SUMMON);
        stand.standControlType(StandControlType.PHENOMENON);
        stand.manualControl(false);
        stand.distanceStrengthDecay(false);
        return stand;
    }

    private static TuskStandType createAct3(ResourceLocation id) {
        TuskStandType stand = createStand(
                id,
                ACT_3,
                new StandStats.Builder()
                        .power(ACT_3_POWER)
                        .speed(ACT_3_SPEED)
                        .range(
                                ACT_3_EFFECTIVE_RANGE,
                                ACT_3_MAX_RANGE)
                        .durability(ACT_3_DURABILITY)
                        .precision(ACT_3_PRECISION)
                        .build(),
                ACT_3_STAND_WIDTH,
                ACT_3_STAND_HEIGHT,
                TuskEntity.ACT_3_FOLLOW_OFFSET,
                () -> JOHNNY_ACT_3_SUMMON);
        stand.standControlType(StandControlType.PHENOMENON);
        stand.manualControl(false);
        stand.distanceStrengthDecay(false);
        return stand;
    }

    private static TuskStandType createAct4(ResourceLocation id) {
        TuskStandType stand = createStand(
                id,
                ACT_4,
                new StandStats.Builder()
                        .power(ACT_4_POWER)
                        .speed(ACT_4_SPEED)
                        .range(
                                ACT_4_EFFECTIVE_RANGE,
                                ACT_4_MAX_RANGE)
                        .durability(ACT_4_DURABILITY)
                        .precision(ACT_4_PRECISION)
                        .build(),
                ACT_4_STAND_WIDTH,
                ACT_4_STAND_HEIGHT,
                TuskEntity.ACT_4_FOLLOW_OFFSET,
                () -> JOHNNY_ACT_4_SUMMON);
        stand.standControlType(StandControlType.CLOSE_RANGE_DIRECT);
        stand.manualControl(true);
        stand.distanceStrengthDecay(true);
        return stand;
    }

    private static TuskStandType createStand(
            ResourceLocation id,
            int actIndex,
            StandStats stats,
            float width,
            float height,
            net.minecraft.world.phys.Vec3 followOffset,
            Supplier<DeferredHolder<SoundEvent, SoundEvent>>
                    summonShout) {
        MovesetBuilder moveset = new MovesetBuilder();
        if (actIndex == ACT_4) {
            moveset.addHumanoidStandStuff();
        }
        moveset
                .addAbility(NAIL_SCRATCH_NAME, NAIL_SCRATCH)
                .addAbility(NAIL_SHOT_NAME, NAIL_SHOT)
                .addAbility(SELECT_ACT_NAME, SELECT_ACT);
        if (actIndex == ACT_4) {
            moveset.addAbility("guard", ModStandAbilities.GUARD);
        } else {
            moveset.addAbility(BLOCK_NAME, ModStandAbilities.GUARD);
        }
        if (actIndex >= ACT_2) {
            moveset.addAbility(
                    CHARGED_NAIL_SHOT_NAME, CHARGED_NAIL_SHOT);
        }
        if (actIndex >= ACT_3) {
            moveset
                    .addAbility(WORMHOLE_NAME, CREATE_WORMHOLE)
                    .addAbility(
                            MOVE_WORMHOLE_NAME, MOVE_WORMHOLE)
                    .addAbility(
                            REMOVE_WORMHOLE_NAME, REMOVE_WORMHOLE)
                    .addAbility(
                            WORMHOLE_ARM_NAME,
                            CREATE_WORMHOLE_ARM)
                    .addAbility(
                            MOVE_WORMHOLE_ARM_NAME,
                            MOVE_WORMHOLE_ARM)
                    .addAbility(
                            REMOVE_WORMHOLE_ARM_NAME,
                            REMOVE_WORMHOLE_ARM);
        }
        if (actIndex == ACT_4) {
            moveset
                    .addAbility(
                            ACT_4_AWARENESS_NAME,
                            ACT_4_AWARENESS)
                    .addAbility(
                            TUSK_PUNCH_NAME,
                            TUSK_PUNCH_TYPE,
                            punch -> punch
                                    .punchImpactSound(TUSK_PUNCH_LIGHT))
                    .addAbility(
                            TUSK_BARRAGE_NAME,
                            ModStandAbilities.BARRAGE,
                            barrage -> barrage
                                    .barrageHitSound(TUSK_PUNCH_LIGHT)
                                    .barrageCrySound(JOHNNY_BARRAGE))
                    .addAbility(
                            TUSK_HEAVY_PUNCH_NAME,
                            TUSK_HEAVY_PUNCH_TYPE,
                            punch -> punch
                                    .heavyPunchImpactSound(
                                            TUSK_PUNCH_HEAVY))
                    .addAbility(
                            "heavy_charged",
                            ModStandAbilities.HEAVY_CHARGED)
                    .addAbility(
                            TUSK_UPPERCUT_NAME,
                            TUSK_UPPERCUT,
                            punch -> {
                                punch.heavyPunchImpactSound(
                                                TUSK_PUNCH_HEAVY);
                                punch.initIsFinisher(
                                        TUSK_HEAVY_PUNCH_NAME);
                            })
                    .addAbility(
                            INFINITE_ROTATION_NAME,
                            TUSK_INFINITE_ROTATION)
                    .addAbility(
                            OPEN_SPACE_NAME,
                            TUSK_OPEN_SPACE)
                    .addAbility("grab", ModStandAbilities.GRAB)
                    .addAbility(
                            "grab_throw", ModStandAbilities.GRAB_THROW)
                    .addAbility(
                            "grab_punch",
                            TUSK_PUNCH_TYPE,
                            punch -> {
                                punch.punchImpactSound(TUSK_PUNCH_LIGHT);
                                punch.initIsGrabVariation();
                            })
                    .addAbility(
                            "grab_barrage",
                            ModStandAbilities.BARRAGE,
                            barrage -> {
                                barrage.barrageHitSound(TUSK_PUNCH_LIGHT)
                                        .barrageCrySound(JOHNNY_BARRAGE);
                                barrage.initIsGrabVariation();
                            })
                    .addAbility(
                            "grab_heavy_punch",
                            TUSK_HEAVY_PUNCH_TYPE,
                            punch -> {
                                punch.heavyPunchImpactSound(
                                                TUSK_PUNCH_HEAVY);
                                punch.initIsGrabVariation();
                            });
        }

        var hotbar = moveset.makeControlScheme("hotbar");
        if (actIndex == ACT_4) {
            hotbar
                    .bind(
                            TUSK_PUNCH_NAME,
                            InputMethod.CLICK,
                            InputKey.LMB)
                    .bind(
                            TUSK_BARRAGE_NAME,
                            InputMethod.HOLD,
                            InputKey.LMB)
                    .bind(
                            TUSK_HEAVY_PUNCH_NAME,
                            InputMethod.CLICK,
                            InputKey.RMB)
                    .bind(
                            "heavy_charged",
                            InputMethod.HOLD,
                            MovesetBuilder.DEFAULT_CHARGED_HEAVY_INPUT)
                    .bind(
                            "grab",
                            InputMethod.CLICK,
                            MovesetBuilder.DEFAULT_GRAB_INPUT)
                    .bind(
                            "grab_throw",
                            InputMethod.HOLD,
                            MovesetBuilder.DEFAULT_GRAB_THROW_INPUT)
                    .bind("guard", InputMethod.HOLD, InputKey.RMB);
        }
        hotbar.makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL);
        if (actIndex == ACT_4) {
            hotbar
                    .addToHotbar(
                            NAIL_SCRATCH_NAME, 0, InputMethod.CLICK)
                    .addToHotbar(
                            NAIL_SHOT_NAME, 0, InputMethod.HOLD);
        }
        hotbar.addToHotbar(
                SELECT_ACT_NAME, 0, InputMethod.CLICK);
        if (actIndex >= ACT_2) {
            hotbar.addToHotbar(
                    CHARGED_NAIL_SHOT_NAME, 0, InputMethod.HOLD);
        }
        if (actIndex >= ACT_3) {
            hotbar
                    .addToHotbar(
                            WORMHOLE_NAME, 0, InputMethod.CLICK)
                    .addToHotbar(
                            MOVE_WORMHOLE_NAME, 0, InputMethod.HOLD)
                    .addToHotbar(
                            REMOVE_WORMHOLE_NAME,
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            WORMHOLE_ARM_NAME, 0, InputMethod.CLICK)
                    .addToHotbar(
                            MOVE_WORMHOLE_ARM_NAME,
                            0,
                            InputMethod.HOLD)
                    .addToHotbar(
                            REMOVE_WORMHOLE_ARM_NAME,
                            0,
                            InputMethod.CLICK);
        }
        if (actIndex == ACT_4) {
            hotbar.addToHotbar(
                    OPEN_SPACE_NAME, 0, InputMethod.CLICK);
        }
        hotbar.finalizeControlScheme();

        var keybinds = moveset.makeControlScheme("keybinds");
        if (actIndex == ACT_4) {
            keybinds
                    .bind(
                            TUSK_PUNCH_NAME,
                            InputMethod.CLICK,
                            InputKey.LMB)
                    .bind(
                            TUSK_BARRAGE_NAME,
                            InputMethod.HOLD,
                            InputKey.LMB)
                    .bind(
                            TUSK_HEAVY_PUNCH_NAME,
                            InputMethod.CLICK,
                            InputKey.RMB)
                    .bind(
                            "heavy_charged",
                            InputMethod.HOLD,
                            MovesetBuilder.DEFAULT_CHARGED_HEAVY_INPUT)
                    .bind(
                            "grab",
                            InputMethod.CLICK,
                            MovesetBuilder.DEFAULT_GRAB_INPUT)
                    .bind(
                            "grab_throw",
                            InputMethod.HOLD,
                            MovesetBuilder.DEFAULT_GRAB_THROW_INPUT)
                    .bind("guard", InputMethod.HOLD, InputKey.RMB);
        } else {
            keybinds
                    .bind(
                            NAIL_SCRATCH_NAME,
                            InputMethod.CLICK,
                            InputKey.LMB)
                    .bind(
                            NAIL_SHOT_NAME,
                            InputMethod.HOLD,
                            InputKey.LMB)
                    .bind(
                            BLOCK_NAME,
                            InputMethod.HOLD,
                            InputKey.RMB);
        }
        keybinds.finalizeControlScheme();

        moveset
                .addSkill(StandUnlockableSkill.startingAbility(
                        NAIL_SCRATCH_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        NAIL_SHOT_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        SELECT_ACT_NAME));
        if (actIndex != ACT_4) {
            moveset.addSkill(
                    StandUnlockableSkill.startingAbility(BLOCK_NAME));
        }

        if (actIndex >= ACT_2) {
            moveset.addSkill(StandUnlockableSkill.startingAbility(
                    CHARGED_NAIL_SHOT_NAME));
        }
        if (actIndex >= ACT_3) {
            moveset
                    .addSkill(StandUnlockableSkill.startingAbility(
                            WORMHOLE_NAME))
                    .addSkill(StandUnlockableSkill.startingAbility(
                            MOVE_WORMHOLE_NAME))
                    .addSkill(StandUnlockableSkill.startingAbility(
                            REMOVE_WORMHOLE_NAME))
                    .addSkill(StandUnlockableSkill.startingAbility(
                            WORMHOLE_ARM_NAME))
                    .addSkill(StandUnlockableSkill.startingAbility(
                            MOVE_WORMHOLE_ARM_NAME))
                    .addSkill(StandUnlockableSkill.startingAbility(
                            REMOVE_WORMHOLE_ARM_NAME));
        }
        if (actIndex == ACT_4) {
            moveset
                    .addSkill(StandUnlockableSkill.startingAbility(
                            ACT_4_AWARENESS_NAME))
                    .addSkill(StandUnlockableSkill.startingAbility(
                            TUSK_PUNCH_NAME))
                    .addSkill(StandUnlockableSkill.startingAbility(
                            TUSK_BARRAGE_NAME))
                    .addSkill(StandUnlockableSkill.startingAbility(
                            TUSK_HEAVY_PUNCH_NAME))
                    .addSkill(StandUnlockableSkill.startingAbility(
                                    "heavy_charged")
                            .prerequisiteSkill(
                                    TUSK_HEAVY_PUNCH_NAME))
                    .addSkill(StandUnlockableSkill.startingAbility(
                            TUSK_UPPERCUT_NAME))
                    .addSkill(StandUnlockableSkill.startingAbility(
                            INFINITE_ROTATION_NAME))
                    .addSkill(StandUnlockableSkill.startingAbility(
                            OPEN_SPACE_NAME))
                    .addSkill(StandUnlockableSkill.startingAbility(
                            "grab"))
                    .addSkill(StandUnlockableSkill.unlockableAbility(
                                    "grab_throw", 100)
                            .withAbility(
                                    "grab_punch",
                                    "grab_barrage",
                                    "grab_heavy_punch")
                            .prerequisiteSkill("grab"))
                    .addHumanoidStandSkills();
        }

        return new TuskStandType(
                stats,
                moveset,
                TUSK_ENTITY.get(),
                id,
                actIndex,
                followOffset,
                actIndex == ACT_4)
                .standDimensions(width, height)
                .summonShout(summonShout)
                .summonSound(() -> TUSK_SUMMON)
                .unsummonSound(() -> TUSK_UNSUMMON)
                .ost(() -> TUSK_OST);
    }
}
