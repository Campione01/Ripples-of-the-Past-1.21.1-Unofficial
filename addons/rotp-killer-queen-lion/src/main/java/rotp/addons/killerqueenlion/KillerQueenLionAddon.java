package rotp.addons.killerqueenlion;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

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
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;

import rotp.addons.killerqueenlion.ability.PrimaryBombAbility;
import rotp.addons.killerqueenlion.ability.BitesTheDustAbility;
import rotp.addons.killerqueenlion.ability.BitesTheDustAbility.Operation;
import rotp.addons.killerqueenlion.ability.BlockBombAbility;
import rotp.addons.killerqueenlion.ability.BlockBombClearAbility;
import rotp.addons.killerqueenlion.ability.BlockBombDetonateAbility;
import rotp.addons.killerqueenlion.ability.BubbleBlockDetonateAbility;
import rotp.addons.killerqueenlion.ability.BubbleMarkAbility;
import rotp.addons.killerqueenlion.ability.DetonateBombProjectilesAbility;
import rotp.addons.killerqueenlion.ability.ItemBombAbility;
import rotp.addons.killerqueenlion.ability.ItemBombDetonateAbility;
import rotp.addons.killerqueenlion.ability.KillerQueenComboPunchAbility;
import rotp.addons.killerqueenlion.ability.KillerQueenStandCombat;
import rotp.addons.killerqueenlion.ability.LaunchBombProjectileAbility;
import rotp.addons.killerqueenlion.ability.PrimaryBombClearAbility;
import rotp.addons.killerqueenlion.ability.PrimaryBombDetonateAbility;
import rotp.addons.killerqueenlion.ability.SheerHeartAttackAbility;
import rotp.addons.killerqueenlion.ability.SheerHeartRecallAbility;
import rotp.addons.killerqueenlion.btd.BitesTheDustUnlockableSkill;
import rotp.addons.killerqueenlion.entity.BubbleBombEntity;
import rotp.addons.killerqueenlion.entity.BubbleMarkEntity;
import rotp.addons.killerqueenlion.entity.PrimaryBombMarkerEntity;
import rotp.addons.killerqueenlion.entity.SheerHeartAttackEntity;
import rotp.addons.killerqueenlion.entity.SnowBombEntity;
import rotp.addons.killerqueenlion.network.KillerQueenStateSyncPayload;
import rotp.addons.killerqueenlion.state.KillerQueenState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@Mod(KillerQueenLionAddon.MOD_ID)
public final class KillerQueenLionAddon {
    public static final String MOD_ID = "rotp_zkq8";
    public static final String PUNCH_NAME = "kq_punch";
    public static final String BARRAGE_NAME = "kq_barrage";
    public static final String COMBO_PUNCH_NAME = "kq_combo_punch";
    public static final String HEAVY_PUNCH_NAME = "kq_heavy_punch";
    // port-only charged heavy, kept beside the 1.16 finisher
    public static final String HEAVY_CHARGED_NAME = "heavy_charged";
    public static final String BLOCK_NAME = "kq_block";
    public static final String PRIMARY_BOMB_NAME = "kq_entitybomb";
    public static final String PRIMARY_BOMB_DETONATE_NAME =
            "kq_entity_explo";
    public static final String PRIMARY_BOMB_CLEAR_NAME =
            "kq_entity_quit";
    public static final String ITEM_BOMB_NAME = "kq_itembomb";
    public static final String ITEM_BOMB_DETONATE_NAME =
            "kq_item_explo";
    public static final String BLOCK_BOMB_NAME = "kq_block_bomb";
    public static final String BLOCK_BOMB_DETONATE_NAME =
            "kq_block_explode";
    public static final String BLOCK_BOMB_CLEAR_NAME =
            "kq_block_quit";
    public static final String SHEER_HEART_NAME = "sheer_heart";
    public static final String SHEER_HEART_RECALL_NAME =
            "shear_heart_back";
    public static final String SNOW_BOMB_NAME = "kq_snow_bomb";
    public static final String BUBBLE_BOMB_NAME =
            "kq_bubble_bomb";
    public static final String PROJECTILE_DETONATE_NAME =
            "snow_explode";
    public static final String BUBBLE_MARK_NAME =
            "kq_bubble_mark";
    public static final String BUBBLE_BLOCK_DETONATE_NAME =
            "kq_bubble_block_explode";
    public static final String TIME_MARK_NAME = "time_mark";
    public static final String RESET_DAY_NAME = "kq_reset";
    public static final String REMOVE_MARK_NAME = "remov_mark";
    public static final String PUT_DUST_NAME = "put_dust";
    public static final String QUIT_DUST_NAME = "quit_dust";

    private static final int REQUIRED_CORE_ABI = 1;

    public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES =
            DeferredRegister.create(JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<StandType> STAND_TYPES =
            DeferredRegister.create(JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);
    public static final DeferredRegister<AttachmentType<?>>
            ATTACHMENT_TYPES = DeferredRegister.create(
                    NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID);

    public static final Supplier<AttachmentType<KillerQueenState>>
            KILLER_QUEEN_STATE = ATTACHMENT_TYPES.register(
                    "killer_queen_state",
                    () -> AttachmentType.serializable(
                                    holder -> holder instanceof Player
                                            ? new KillerQueenState()
                                            : null)
                            .build());

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<PrimaryBombAbility>>
            PRIMARY_BOMB = ABILITY_TYPES.register(
                    PRIMARY_BOMB_NAME,
                    key -> new AbilityType<>(
                            key, PrimaryBombAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<PrimaryBombDetonateAbility>>
            PRIMARY_BOMB_DETONATE = ABILITY_TYPES.register(
                    PRIMARY_BOMB_DETONATE_NAME,
                    key -> new AbilityType<>(
                            key, PrimaryBombDetonateAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<PrimaryBombClearAbility>>
            PRIMARY_BOMB_CLEAR = ABILITY_TYPES.register(
                    PRIMARY_BOMB_CLEAR_NAME,
                    key -> new AbilityType<>(
                            key, PrimaryBombClearAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<KillerQueenStandCombat.Punch>>
            PUNCH = ABILITY_TYPES.register(
                    PUNCH_NAME,
                    key -> new AbilityType<>(
                            key, KillerQueenStandCombat.Punch::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<KillerQueenStandCombat.Barrage>>
            BARRAGE = ABILITY_TYPES.register(
                    BARRAGE_NAME,
                    key -> new AbilityType<>(
                            key, KillerQueenStandCombat.Barrage::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<KillerQueenStandCombat.HeavyPunch>>
            HEAVY_PUNCH = ABILITY_TYPES.register(
                    HEAVY_PUNCH_NAME,
                    key -> new AbilityType<>(
                            key, KillerQueenStandCombat.HeavyPunch::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<KillerQueenStandCombat.ChargedHeavy>>
            HEAVY_CHARGED = ABILITY_TYPES.register(
                    HEAVY_CHARGED_NAME,
                    key -> new AbilityType<>(
                            key, KillerQueenStandCombat.ChargedHeavy::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<KillerQueenStandCombat.Block>>
            BLOCK = ABILITY_TYPES.register(
                    BLOCK_NAME,
                    key -> new AbilityType<>(
                            key, KillerQueenStandCombat.Block::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<KillerQueenComboPunchAbility>>
            COMBO_PUNCH = ABILITY_TYPES.register(
                    COMBO_PUNCH_NAME,
                    key -> new AbilityType<>(
                            key, KillerQueenComboPunchAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<ItemBombAbility>>
            ITEM_BOMB = ABILITY_TYPES.register(
                    ITEM_BOMB_NAME,
                    key -> new AbilityType<>(
                            key, ItemBombAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<ItemBombDetonateAbility>>
            ITEM_BOMB_DETONATE = ABILITY_TYPES.register(
                    ITEM_BOMB_DETONATE_NAME,
                    key -> new AbilityType<>(
                            key, ItemBombDetonateAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<BlockBombAbility>>
            BLOCK_BOMB = ABILITY_TYPES.register(
                    BLOCK_BOMB_NAME,
                    key -> new AbilityType<>(
                            key, BlockBombAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<BlockBombDetonateAbility>>
            BLOCK_BOMB_DETONATE = ABILITY_TYPES.register(
                    BLOCK_BOMB_DETONATE_NAME,
                    key -> new AbilityType<>(
                            key, BlockBombDetonateAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<BlockBombClearAbility>>
            BLOCK_BOMB_CLEAR = ABILITY_TYPES.register(
                    BLOCK_BOMB_CLEAR_NAME,
                    key -> new AbilityType<>(
                            key, BlockBombClearAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<SheerHeartAttackAbility>>
            SHEER_HEART_ATTACK = ABILITY_TYPES.register(
                    SHEER_HEART_NAME,
                    key -> new AbilityType<>(
                            key, SheerHeartAttackAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<SheerHeartRecallAbility>>
            SHEER_HEART_RECALL = ABILITY_TYPES.register(
                    SHEER_HEART_RECALL_NAME,
                    key -> new AbilityType<>(
                            key, SheerHeartRecallAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<LaunchBombProjectileAbility>>
            SNOW_BOMB = ABILITY_TYPES.register(
                    SNOW_BOMB_NAME,
                    key -> new AbilityType<>(
                            key,
                            (type, id) ->
                                    new LaunchBombProjectileAbility(
                                            type,
                                            id,
                                            LaunchBombProjectileAbility
                                                    .Kind.SNOW)));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<LaunchBombProjectileAbility>>
            BUBBLE_BOMB = ABILITY_TYPES.register(
                    BUBBLE_BOMB_NAME,
                    key -> new AbilityType<>(
                            key,
                            (type, id) ->
                                    new LaunchBombProjectileAbility(
                                            type,
                                            id,
                                            LaunchBombProjectileAbility
                                                    .Kind.BUBBLE)));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<BubbleMarkAbility>>
            BUBBLE_MARK = ABILITY_TYPES.register(
                    BUBBLE_MARK_NAME,
                    key -> new AbilityType<>(
                            key, BubbleMarkAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<BubbleBlockDetonateAbility>>
            BUBBLE_BLOCK_DETONATE = ABILITY_TYPES.register(
                    BUBBLE_BLOCK_DETONATE_NAME,
                    key -> new AbilityType<>(
                            key, BubbleBlockDetonateAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<DetonateBombProjectilesAbility>>
            PROJECTILE_DETONATE = ABILITY_TYPES.register(
                    PROJECTILE_DETONATE_NAME,
                    key -> new AbilityType<>(
                            key,
                            DetonateBombProjectilesAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<BitesTheDustAbility>>
            TIME_MARK = ABILITY_TYPES.register(
                    TIME_MARK_NAME,
                    key -> new AbilityType<>(
                            key,
                            (type, id) -> new BitesTheDustAbility(
                                    type, id, Operation.TIME_MARK)));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<BitesTheDustAbility>>
            RESET_DAY = ABILITY_TYPES.register(
                    RESET_DAY_NAME,
                    key -> new AbilityType<>(
                            key,
                            (type, id) -> new BitesTheDustAbility(
                                    type, id, Operation.RESET_DAY)));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<BitesTheDustAbility>>
            REMOVE_MARK = ABILITY_TYPES.register(
                    REMOVE_MARK_NAME,
                    key -> new AbilityType<>(
                            key,
                            (type, id) -> new BitesTheDustAbility(
                                    type, id, Operation.REMOVE_MARK)));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<BitesTheDustAbility>>
            PUT_DUST = ABILITY_TYPES.register(
                    PUT_DUST_NAME,
                    key -> new AbilityType<>(
                            key,
                            (type, id) -> new BitesTheDustAbility(
                                    type, id, Operation.PUT_DUST)));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<BitesTheDustAbility>>
            QUIT_DUST = ABILITY_TYPES.register(
                    QUIT_DUST_NAME,
                    key -> new AbilityType<>(
                            key,
                            (type, id) -> new BitesTheDustAbility(
                                    type, id, Operation.QUIT_DUST)));

    public static final DeferredHolder<
            EntityType<?>, EntityType<StandEntity>>
            KILLER_QUEEN_ENTITY = ENTITY_TYPES.register(
                    "killer_queen",
                    key -> EntityType.Builder.<StandEntity>of(
                                    StandEntity::new, MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(
                                    KillerQueenPhase1Spec.ENTITY_WIDTH,
                                    KillerQueenPhase1Spec.ENTITY_HEIGHT)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));
    public static final DeferredHolder<
            EntityType<?>, EntityType<SnowBombEntity>>
            SNOW_BOMB_ENTITY = ENTITY_TYPES.register(
                    "snow_bomb",
                    key -> EntityType.Builder
                            .<SnowBombEntity>of(
                                    SnowBombEntity::new,
                                    MobCategory.MISC)
                            .noSummon()
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(32)
                            .updateInterval(1)
                            .build(key.toString()));
    public static final DeferredHolder<
            EntityType<?>, EntityType<BubbleBombEntity>>
            BUBBLE_BOMB_ENTITY = ENTITY_TYPES.register(
                    "bubble_bomb",
                    key -> EntityType.Builder
                            .<BubbleBombEntity>of(
                                    BubbleBombEntity::new,
                                    MobCategory.MISC)
                            .noSummon()
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(32)
                            .updateInterval(1)
                            .build(key.toString()));
    public static final DeferredHolder<
            EntityType<?>, EntityType<BubbleMarkEntity>>
            BUBBLE_MARK_ENTITY = ENTITY_TYPES.register(
                    "bubble_mark",
                    key -> EntityType.Builder
                            .<BubbleMarkEntity>of(
                                    BubbleMarkEntity::new,
                                    MobCategory.MISC)
                            .noSummon()
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(32)
                            .updateInterval(1)
                            .build(key.toString()));
    public static final DeferredHolder<
            EntityType<?>, EntityType<SheerHeartAttackEntity>>
            SHEER_HEART_ENTITY = ENTITY_TYPES.register(
                    "sheer_heart",
                    key -> EntityType.Builder
                            .<SheerHeartAttackEntity>of(
                                    SheerHeartAttackEntity::new,
                                    MobCategory.MISC)
                            .fireImmune()
                            .sized(0.85F, 0.7F)
                            .clientTrackingRange(64)
                            .updateInterval(2)
                            .build(key.toString()));
    public static final DeferredHolder<
            EntityType<?>, EntityType<PrimaryBombMarkerEntity>>
            PRIMARY_BOMB_MARKER_ENTITY = ENTITY_TYPES.register(
                    "primary_bomb_marker",
                    key -> EntityType.Builder
                            .<PrimaryBombMarkerEntity>of(
                                    PrimaryBombMarkerEntity::new,
                                    MobCategory.MISC)
                            .noSummon()
                            .fireImmune()
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(64)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<SoundEvent, SoundEvent>
            KILLER_QUEEN_SUMMON = sound("killer_queen_summon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            KILLER_QUEEN_UNSUMMON = sound("killer_queen_unsummon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            KILLER_QUEEN_PUNCH = sound("killer_queen_punch");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            KILLER_QUEEN_PUNCH_HEAVY =
            sound("killer_queen_punch_heavy");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            KILLER_QUEEN_SHIBA = sound("killer_queen_shiba");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            KILLER_QUEEN_SHIBA_LONG =
            sound("killer_queen_shiba_long");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            KILLER_QUEEN_SHIBA_BA_BA =
            sound("killer_queen_shiba_ba_ba");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            KILLER_QUEEN_BOOM = sound("killer_queen_boom");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            KILLER_QUEEN_UNBOOM = sound("killer_queen_unbomb");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            USER_KIRA = sound("user_kira");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            KIRA_SHEER_HEART = sound("kira_sheer_heart");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SHEER_HEART = sound("sheer_heart");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SHEER_HEART_STEP = sound("sheer_heart_step");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            BITES_THE_DUST = sound("bites_za_dust");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            USER_BITES_THE_DUST =
            sound("user_bites_za_dust");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            VOID = sound("void");

    public static final DeferredHolder<StandType, EntityStandType>
            KILLER_QUEEN = STAND_TYPES.register(
                    "killer_queen", KillerQueenLionAddon::createStand);

    public KillerQueenLionAddon(IEventBus modEventBus) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_VISUAL_CONTEXT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_TIME_STOP_LIFECYCLE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_SHOUTS_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ACTION_STAND_OFFSET_HOOK_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_POWER_ACTION_SLOT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_ACTION_PORT_SLOT_V1)) {
            throw new IllegalStateException(
                    "RotP Killer Queen requires ROTP addon ABI 1 with "
                            + "namespaced ability resources, Stand visual "
                            + "contexts, time-stop lifecycle events, ability "
                            + "shouts, the action Stand offset hook, the "
                            + "Stand-power action slot and the Stand action "
                            + "port slot.");
        }

        ABILITY_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        ATTACHMENT_TYPES.register(modEventBus);
        modEventBus.addListener(KillerQueenLionAddon::registerPayloads);
        modEventBus.addListener(
                KillerQueenEvents::registerAttributes);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static KillerQueenState state(Player player) {
        return player.getData(KILLER_QUEEN_STATE);
    }

    public static boolean isKillerQueenUser(Player player) {
        StandPower power = StandPower.get(player);
        return power != null
                && power.getPowerType() == KILLER_QUEEN.get();
    }

    public static void syncState(ServerPlayer player) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                player,
                KillerQueenStateSyncPayload.from(
                        player, state(player)));
    }

    public static void syncState(
            ServerPlayer observer, Player owner) {
        PacketDistributor.sendToPlayer(
                observer,
                KillerQueenStateSyncPayload.from(
                        owner, state(owner)));
    }

    private static void registerPayloads(
            RegisterPayloadHandlersEvent event) {
        event.registrar("4").playToClient(
                KillerQueenStateSyncPayload.TYPE,
                KillerQueenStateSyncPayload.STREAM_CODEC,
                KillerQueenStateSyncPayload::handle);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(
            String name) {
        return SOUND_EVENTS.register(
                name, SoundEvent::createVariableRangeEvent);
    }

    private static EntityStandType createStand(ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addHumanoidStandStuff()
                .addAbility(
                        PUNCH_NAME,
                        PUNCH,
                        punch -> {
                            punch.punchImpactSound(KILLER_QUEEN_PUNCH);
                            punch.standCrySound(KILLER_QUEEN_SHIBA);
                        })
                .addAbility(
                        BARRAGE_NAME,
                        BARRAGE,
                        barrage -> {
                            barrage.barrageHitSound(KILLER_QUEEN_PUNCH);
                            barrage.barrageCrySound(
                                    KILLER_QUEEN_SHIBA_BA_BA);
                            // 1.16 one-argument standSound: the cry also
                            // plays while the Stand is arms-only
                            barrage.barrageCryInArmsOnly();
                        })
                .addAbility(
                        HEAVY_PUNCH_NAME,
                        HEAVY_PUNCH,
                        punch -> {
                            punch.heavyPunchImpactSound(
                                    KILLER_QUEEN_PUNCH_HEAVY);
                            punch.heavyPunchCrySound(
                                    KILLER_QUEEN_SHIBA_LONG);
                        })
                .addAbility(
                        HEAVY_CHARGED_NAME,
                        HEAVY_CHARGED)
                // 1.16 KQ_HEAVY_PUNCH.setFinisherVariation(KQ_COMBO_PUNCH)
                .addAbility(
                        COMBO_PUNCH_NAME,
                        COMBO_PUNCH,
                        punch -> {
                            punch.heavyPunchImpactSound(
                                    KILLER_QUEEN_PUNCH_HEAVY);
                            punch.heavyPunchCrySound(
                                    KILLER_QUEEN_SHIBA_LONG);
                            punch.initIsFinisher(HEAVY_PUNCH_NAME);
                        })
                .addAbility(BLOCK_NAME, BLOCK)
                .addAbility(PRIMARY_BOMB_NAME, PRIMARY_BOMB)
                .addAbility(
                        PRIMARY_BOMB_DETONATE_NAME,
                        PRIMARY_BOMB_DETONATE)
                .addAbility(
                        PRIMARY_BOMB_CLEAR_NAME,
                        PRIMARY_BOMB_CLEAR)
                .addAbility(ITEM_BOMB_NAME, ITEM_BOMB)
                .addAbility(
                        ITEM_BOMB_DETONATE_NAME,
                        ITEM_BOMB_DETONATE)
                .addAbility(BLOCK_BOMB_NAME, BLOCK_BOMB)
                .addAbility(
                        BLOCK_BOMB_DETONATE_NAME,
                        BLOCK_BOMB_DETONATE)
                .addAbility(
                        BLOCK_BOMB_CLEAR_NAME,
                        BLOCK_BOMB_CLEAR)
                .addAbility(
                        SHEER_HEART_NAME,
                        SHEER_HEART_ATTACK)
                .addAbility(
                        SHEER_HEART_RECALL_NAME,
                        SHEER_HEART_RECALL)
                .addAbility(SNOW_BOMB_NAME, SNOW_BOMB)
                .addAbility(BUBBLE_BOMB_NAME, BUBBLE_BOMB)
                .addAbility(BUBBLE_MARK_NAME, BUBBLE_MARK)
                .addAbility(
                        BUBBLE_BLOCK_DETONATE_NAME,
                        BUBBLE_BLOCK_DETONATE)
                .addAbility(
                        PROJECTILE_DETONATE_NAME,
                        PROJECTILE_DETONATE)
                .addAbility(TIME_MARK_NAME, TIME_MARK)
                .addAbility(RESET_DAY_NAME, RESET_DAY)
                .addAbility(REMOVE_MARK_NAME, REMOVE_MARK)
                .addAbility(PUT_DUST_NAME, PUT_DUST)
                .addAbility(QUIT_DUST_NAME, QUIT_DUST)
                .makeControlScheme("hotbar")
                    .bind(PUNCH_NAME, InputMethod.CLICK, InputKey.LMB)
                    .bind(BARRAGE_NAME, InputMethod.HOLD, InputKey.LMB)
                    .bind(
                            HEAVY_PUNCH_NAME,
                            InputMethod.CLICK,
                            InputKey.RMB)
                    .bind(
                            HEAVY_CHARGED_NAME,
                            InputMethod.HOLD,
                            MovesetBuilder.DEFAULT_CHARGED_HEAVY_INPUT)
                    .bind(BLOCK_NAME, InputMethod.HOLD, InputKey.RMB)
                    .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
                    .addToHotbar(
                            PRIMARY_BOMB_NAME,
                            0,
                            InputMethod.CLICK)
                    // 1.16 PunchBomb.replaceAction: detonation takes the
                    // plant's own slot once a bomb is in range
                    .addHotbarSlotVariation(
                            PRIMARY_BOMB_DETONATE_NAME,
                            PRIMARY_BOMB_NAME,
                            null,
                            InputMethod.HOLD)
                    .addToHotbar(
                            PRIMARY_BOMB_CLEAR_NAME,
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            ITEM_BOMB_NAME,
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            ITEM_BOMB_DETONATE_NAME,
                            0,
                            InputMethod.HOLD)
                    .addToHotbar(
                            BLOCK_BOMB_NAME,
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            BLOCK_BOMB_CLEAR_NAME,
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            SHEER_HEART_NAME,
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            SHEER_HEART_RECALL_NAME,
                            0,
                            InputMethod.HOLD)
                    .addToHotbar(
                            SNOW_BOMB_NAME,
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            BUBBLE_BOMB_NAME,
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            BUBBLE_MARK_NAME,
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            BUBBLE_BLOCK_DETONATE_NAME,
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            PROJECTILE_DETONATE_NAME,
                            0,
                            InputMethod.HOLD)
                    .addToHotbar(
                            TIME_MARK_NAME,
                            0,
                            InputMethod.HOLD)
                .finalizeControlScheme()
                .makeControlScheme("keybinds")
                    .bind(PUNCH_NAME, InputMethod.CLICK, InputKey.LMB)
                    .bind(BARRAGE_NAME, InputMethod.HOLD, InputKey.LMB)
                    .bind(
                            HEAVY_PUNCH_NAME,
                            InputMethod.CLICK,
                            InputKey.RMB)
                    .bind(
                            HEAVY_CHARGED_NAME,
                            InputMethod.HOLD,
                            MovesetBuilder.DEFAULT_CHARGED_HEAVY_INPUT)
                    .bind(BLOCK_NAME, InputMethod.HOLD, InputKey.RMB)
                .finalizeControlScheme()
                .addSkill(StandUnlockableSkill.startingAbility(PUNCH_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(BARRAGE_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                                HEAVY_PUNCH_NAME)
                        .withAbility(COMBO_PUNCH_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                                HEAVY_CHARGED_NAME)
                        .prerequisiteSkill(HEAVY_PUNCH_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(BLOCK_NAME))
                .addSkill(StandUnlockableSkill.unlockableAbility(
                                PRIMARY_BOMB_NAME, 125)
                        .withAbility(
                                PRIMARY_BOMB_DETONATE_NAME,
                                PRIMARY_BOMB_CLEAR_NAME)
                        .prerequisiteSkill(HEAVY_PUNCH_NAME))
                .addSkill(StandUnlockableSkill.unlockableAbility(
                                ITEM_BOMB_NAME, 100)
                        .withAbility(
                                ITEM_BOMB_DETONATE_NAME)
                        .prerequisiteSkill(HEAVY_PUNCH_NAME))
                .addSkill(StandUnlockableSkill.unlockableAbility(
                                BLOCK_BOMB_NAME, 150)
                        .withAbility(
                                BLOCK_BOMB_DETONATE_NAME,
                                BLOCK_BOMB_CLEAR_NAME)
                        .prerequisiteSkill(
                                PRIMARY_BOMB_NAME))
                .addSkill(StandUnlockableSkill.unlockableAbility(
                                SHEER_HEART_NAME, 200)
                        .withAbility(
                                SHEER_HEART_RECALL_NAME)
                        .prerequisiteSkill(
                                PRIMARY_BOMB_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        BUBBLE_BOMB_NAME))
                // 1.16 kq_bubble_mark and its shift variation have no resolve level
                .addSkill(StandUnlockableSkill.startingAbility(
                                BUBBLE_MARK_NAME)
                        .withAbility(BUBBLE_BLOCK_DETONATE_NAME))
                .addSkill(StandUnlockableSkill.unlockableAbility(
                                SNOW_BOMB_NAME, 250)
                        .withAbility(PROJECTILE_DETONATE_NAME)
                        .prerequisiteSkill(
                                PRIMARY_BOMB_NAME))
                .addSkill(new BitesTheDustUnlockableSkill())
                .addSkill(StandUnlockableSkill.startingAbility(
                        "manual_control"));

        return new KillerQueenStandType(
                new StandStats.Builder()
                        .power(KillerQueenPhase1Spec.POWER)
                        .speed(KillerQueenPhase1Spec.SPEED)
                        .range(
                                KillerQueenPhase1Spec.EFFECTIVE_RANGE,
                                KillerQueenPhase1Spec.MAX_RANGE)
                        .durability(KillerQueenPhase1Spec.DURABILITY)
                        .precision(KillerQueenPhase1Spec.PRECISION)
                        .build(),
                moveset,
                KILLER_QUEEN_ENTITY.get(),
                id)
                .standControlType(StandControlType.HYBRID_FORM)
                .manualControl(true)
                .distanceStrengthDecay(true)
                .standDimensions(
                        KillerQueenPhase1Spec.ENTITY_WIDTH,
                        KillerQueenPhase1Spec.ENTITY_HEIGHT)
                .summonShout(() -> USER_KIRA)
                .summonSound(() -> KILLER_QUEEN_SUMMON)
                .unsummonSound(() -> KILLER_QUEEN_UNSUMMON);
    }
}
