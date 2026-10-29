package rotp.addons.kingcrimson;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import java.util.function.Supplier;

import rotp.core.api.RotpAddonApi;
import rotp.core.core.JojoRegistries;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.util.sound.OstSoundList;

import rotp.addons.kingcrimson.ability.KingCrimsonChopAbility;
import rotp.addons.kingcrimson.ability.KingCrimsonBarrageAbility;
import rotp.addons.kingcrimson.ability.KingCrimsonDesperateEyejabAbility;
import rotp.addons.kingcrimson.ability.KingCrimsonEpitaphAbility;
import rotp.addons.kingcrimson.ability.KingCrimsonGroundPunchAbility;
import rotp.addons.kingcrimson.ability.KingCrimsonGroundSlamAbility;
import rotp.addons.kingcrimson.ability.KingCrimsonHeavyPunchAbility;
import rotp.addons.kingcrimson.ability.KingCrimsonImpaleAbility;
import rotp.addons.kingcrimson.ability.KingCrimsonProjectileThrowAbility;
import rotp.addons.kingcrimson.ability.KingCrimsonTimeEraseAbility;
import rotp.addons.kingcrimson.ability.KingCrimsonTimeSkipAbility;
import rotp.addons.kingcrimson.ability.KingCrimsonTripleProjectileThrowAbility;
import rotp.addons.kingcrimson.effect.KingCrimsonBleedingEffect;
import rotp.addons.kingcrimson.effect.KingCrimsonGapingWoundEffect;
import rotp.addons.kingcrimson.effect.KingCrimsonHalfBlindnessEffect;
import rotp.addons.kingcrimson.effect.KingCrimsonMangledBodyEffect;
import rotp.addons.kingcrimson.effect.KingCrimsonTimeEraseEffect;
import rotp.addons.kingcrimson.entity.KingCrimsonAfterimageEntity;
import rotp.addons.kingcrimson.entity.KingCrimsonTimeEraseDecoyEntity;
import rotp.addons.kingcrimson.network.EpitaphForesightPayload;
import rotp.addons.kingcrimson.network.EpitaphStateSyncPayload;
import rotp.addons.kingcrimson.network.TemporalStateSyncPayload;
import rotp.addons.kingcrimson.network.TimeEraseAfterimageRemovePayload;
import rotp.addons.kingcrimson.network.TimeEraseAfterimageSpawnPayload;
import rotp.addons.kingcrimson.network.TimeEraseTransitionPayload;
import rotp.addons.kingcrimson.state.EpitaphState;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@Mod(KingCrimsonAddon.MOD_ID)
public final class KingCrimsonAddon {
    public static final String MOD_ID = "rotp_kingcrimson";
    private static final int REQUIRED_CORE_ABI = 1;

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, MOD_ID);
    public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES =
            DeferredRegister.create(
                    JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<StandType> STAND_TYPES =
            DeferredRegister.create(JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);
    public static final DeferredRegister<AttachmentType<?>>
            ATTACHMENT_TYPES = DeferredRegister.create(
                    NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID);

    public static final Supplier<AttachmentType<EpitaphState>>
            EPITAPH_STATE = ATTACHMENT_TYPES.register(
                    "epitaph_state",
                    () -> AttachmentType.serializable(
                                    holder -> holder instanceof Player
                                            ? new EpitaphState()
                                            : null)
                            .build());
    public static final Supplier<AttachmentType<KingCrimsonTemporalState>>
            TEMPORAL_STATE = ATTACHMENT_TYPES.register(
                    "temporal_state",
                    () -> AttachmentType.serializable(
                                    holder -> holder instanceof Player
                                            ? new KingCrimsonTemporalState()
                                            : null)
                            .build());

    public static final DeferredHolder<EntityType<?>, EntityType<StandEntity>>
            KING_CRIMSON_ENTITY = ENTITY_TYPES.register(
                    "kingcrimson",
                    key -> EntityType.Builder.<StandEntity>of(
                                    StandEntity::new, MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(
                                    KingCrimsonPhase1Spec.ENTITY_WIDTH,
                                    KingCrimsonPhase1Spec.ENTITY_HEIGHT)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));
    public static final DeferredHolder<
            EntityType<?>,
            EntityType<KingCrimsonAfterimageEntity>>
            KC_AFTERIMAGE_ENTITY = ENTITY_TYPES.register(
                    "kc_afterimage",
                    key -> EntityType.Builder
                            .<KingCrimsonAfterimageEntity>of(
                                    KingCrimsonAfterimageEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.6F, 1.8F)
                            .setShouldReceiveVelocityUpdates(false)
                            .setUpdateInterval(Integer.MAX_VALUE)
                            .build(key.toString()));
    public static final DeferredHolder<
            EntityType<?>,
            EntityType<KingCrimsonTimeEraseDecoyEntity>>
            TIME_ERASE_DECOY_ENTITY = ENTITY_TYPES.register(
                    "time_erase_decoy",
                    key -> EntityType.Builder
                            .<KingCrimsonTimeEraseDecoyEntity>of(
                                    KingCrimsonTimeEraseDecoyEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.6F, 1.8F)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<SoundEvent, SoundEvent>
            KINGCRIMSON_SUMMON = sound("kingcrimson_summon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            KINGCRIMSON_UNSUMMON = sound("kingcrimson_unsummon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            KINGCRIMSON_PUNCH = sound("kingcrimson_punch");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            KINGCRIMSON_HEAVYPUNCH = sound("kingcrimson_heavypunch");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIAVOLO_KINGCRIMSON = sound("diavolo_kingcrimson");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIAVOLO_PUNCH = sound("diavolo_punch");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIAVOLO_BARRAGE = sound("diavolo_barrage");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DOPPIO_BARRAGE = sound("doppio_barrage");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIAVOLO_HEAVYPUNCH = sound("diavolo_heavypunch");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            KINGCRIMSON_CHOP = sound("kingcrimson_chop");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            KINGCRIMSON_IMPALE = sound("kingcrimson_impale");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIAVOLO_CHOP = sound("diavolo_chop");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIAVOLO_IMPALE = sound("diavolo_impale");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DOPPIO_THERE_YOU_ARE = sound("doppio_there_you_are");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DOPPIO_DAMNIT = sound("doppio_damnit");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            KINGCRIMSON_EPITAPH = sound("kingcrimson_epitaph");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            KINGCRIMSON_DASH = sound("kingcrimson_dash");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIAVOLO_DASH = sound("diavolo_dash");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIAVOLO_EPITAPH = sound("diavolo_epitaph");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            KINGCRIMSON_GROUNDPUNCH =
                    sound("kingcrimson_groundpunch");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            KINGCRIMSON_DESPERATE_EYEJAB =
                    sound("kingcrimson_desperate_eyejab");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIAVOLO_COMBOFINISHER = sound("diavolo_combofinisher");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DOPPIO_TAKE_THIS = sound("doppio_take_this");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            KINGCRIMSON_BLOODTHROW = sound("kingcrimson_bloodthrow");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            EPITAPH_TIMESKIP = SOUND_EVENTS.register(
                    "a",
                    () -> SoundEvent.createVariableRangeEvent(
                            id("epitaph_timeskip")));
    public static final DeferredHolder<SoundEvent, SoundEvent>
            KINGCRIMSON_TIMESKIP = sound("kingcrimson_timeskip");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            KINGCRIMSON_TIMESKIP2 = sound("kingcrimson_timeskip2");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            KINGCRIMSON_TIMEERASE = sound("kingcrimson_timeerase");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            TIME_ERASE_START = sound("time_erase_start");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            TIME_ERASE_END = sound("time_erase_end");
    public static final OstSoundList KINGCRIMSON_OST =
            new OstSoundList(id("kc_ost"), SOUND_EVENTS);

    public static final DeferredHolder<MobEffect, KingCrimsonBleedingEffect>
            BLEEDING = MOB_EFFECTS.register(
                    "bleeding",
                    KingCrimsonBleedingEffect::new);
    public static final DeferredHolder<
            MobEffect,
            KingCrimsonGapingWoundEffect> GAPING_WOUND =
                    MOB_EFFECTS.register(
                            "gaping_wound",
                            KingCrimsonGapingWoundEffect::new);
    public static final DeferredHolder<
            MobEffect,
            KingCrimsonMangledBodyEffect> MANGLED_BODY =
                    MOB_EFFECTS.register(
                            "mangled_body",
                            KingCrimsonMangledBodyEffect::new);
    public static final DeferredHolder<
            MobEffect,
            KingCrimsonHalfBlindnessEffect> HALF_BLINDNESS_LEFT =
                    MOB_EFFECTS.register(
                            "half_blindness_left",
                            KingCrimsonHalfBlindnessEffect::new);
    public static final DeferredHolder<
            MobEffect,
            KingCrimsonHalfBlindnessEffect> HALF_BLINDNESS_RIGHT =
                    MOB_EFFECTS.register(
                            "half_blindness_right",
                            KingCrimsonHalfBlindnessEffect::new);
    public static final DeferredHolder<
            MobEffect,
            KingCrimsonTimeEraseEffect> TIME_ERASE =
                    MOB_EFFECTS.register(
                            "time_erase",
                            KingCrimsonTimeEraseEffect::new);

    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<KingCrimsonBarrageAbility>>
            KING_CRIMSON_BARRAGE_ABILITY =
                    ABILITY_TYPES.register(
                            "kingcrimson_barrage",
                            key -> new AbilityType<>(
                                    key,
                                    KingCrimsonBarrageAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<KingCrimsonBarrageAbility>>
            KING_CRIMSON_DOPPIO_BARRAGE_ABILITY =
                    ABILITY_TYPES.register(
                            "kingcrimson_doppio_barrage",
                            key -> new AbilityType<>(
                                    key,
                                    KingCrimsonBarrageAbility::doppio));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<KingCrimsonChopAbility>>
            KING_CRIMSON_CHOP_ABILITY = ABILITY_TYPES.register(
                    "kingcrimson_chop",
                    key -> new AbilityType<>(
                            key, KingCrimsonChopAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<KingCrimsonImpaleAbility>>
            KING_CRIMSON_IMPALE_ABILITY = ABILITY_TYPES.register(
                    "kingcrimson_impale",
                    key -> new AbilityType<>(
                            key, KingCrimsonImpaleAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<KingCrimsonProjectileThrowAbility>>
            KING_CRIMSON_PROJECTILE_THROW_ABILITY =
                    ABILITY_TYPES.register(
                            "kingcrimson_projectile_throw",
                            key -> new AbilityType<>(
                                    key,
                                    KingCrimsonProjectileThrowAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<KingCrimsonTripleProjectileThrowAbility>>
            KING_CRIMSON_PROJECTILE_THROW_TRIPPLE_ABILITY =
                    ABILITY_TYPES.register(
                            "kingcrimson_projectile_throw_tripple",
                            key -> new AbilityType<>(
                                    key,
                                    KingCrimsonTripleProjectileThrowAbility
                                            ::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<KingCrimsonEpitaphAbility>>
            KING_CRIMSON_EPITAPH_ABILITY =
                    ABILITY_TYPES.register(
                            "kingcrimson_epitaph",
                            key -> new AbilityType<>(
                                    key,
                                    KingCrimsonEpitaphAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<KingCrimsonHeavyPunchAbility>>
            KING_CRIMSON_HEAVY_PUNCH_ABILITY =
                    ABILITY_TYPES.register(
                            "kingcrimson_heavy_punch",
                            key -> new AbilityType<>(
                                    key,
                                    KingCrimsonHeavyPunchAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<KingCrimsonGroundPunchAbility>>
            KING_CRIMSON_GROUND_PUNCH_ABILITY =
                    ABILITY_TYPES.register(
                            "kingcrimson_groundpunch",
                            key -> new AbilityType<>(
                                    key,
                                    KingCrimsonGroundPunchAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<KingCrimsonGroundSlamAbility>>
            KING_CRIMSON_GROUND_SLAM_ABILITY =
                    ABILITY_TYPES.register(
                            "kingcrimson_groundslam",
                            key -> new AbilityType<>(
                                    key,
                                    KingCrimsonGroundSlamAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<KingCrimsonDesperateEyejabAbility>>
            KING_CRIMSON_DESPERATE_EYEJAB_ABILITY =
                    ABILITY_TYPES.register(
                            "kingcrimson_desperate_eyejab",
                            key -> new AbilityType<>(
                                    key,
                                    KingCrimsonDesperateEyejabAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<KingCrimsonTimeSkipAbility>>
            KING_CRIMSON_TIME_SKIP_ABILITY =
                    ABILITY_TYPES.register(
                            "kingcrimson_timeskip",
                            key -> new AbilityType<>(
                                    key,
                                    KingCrimsonTimeSkipAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<KingCrimsonTimeEraseAbility>>
            KING_CRIMSON_TIME_ERASE_ABILITY =
                    ABILITY_TYPES.register(
                            "kingcrimson_timeerase",
                            key -> new AbilityType<>(
                                    key,
                                    KingCrimsonTimeEraseAbility::new));

    public static final DeferredHolder<StandType, EntityStandType>
            KING_CRIMSON = STAND_TYPES.register(
                    "kingcrimson",
                    KingCrimsonAddon::createStand);

    public KingCrimsonAddon(IEventBus modEventBus, ModContainer modContainer) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_VISUAL_CONTEXT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ADDON_POST_EFFECT_LIFECYCLE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_OBSERVER_WORLD_RENDER_POLICIES_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_CLIENT_SKY_RENDERERS_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_LIVING_ENTITY_MATERIAL_TINT_POLICIES_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_SHOUTS_V1)) {
            throw new IllegalStateException(
                    "RotP King Crimson requires ROTP addon ABI 1 with "
                            + "namespaced ability resources and Stand visual "
                            + "contexts, managed addon post effects, and "
                            + "observer world, sky, and living-material "
                            + "render policies.");
        }
        // the time skip calls Ability.checkPerformerStun (R148 core)
        if (!RotpAddonApi.supportsFeature(
                RotpAddonApi.FEATURE_ABILITY_PERFORMER_STUN_V1)) {
            throw new IllegalStateException(
                    "RotP King Crimson requires the core performer stun "
                            + "check (a core from the same release).");
        }
        // the time skip's player action lives in its user's Stand-power slot
        if (!RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_POWER_ACTION_SLOT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_PLAYER_ACTION_GATE_V1)) {
            throw new IllegalStateException(
                    "RotP King Crimson requires the core Stand-power "
                            + "action slot and player action gate (a core "
                            + "from the same release).");
        }

        ENTITY_TYPES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        MOB_EFFECTS.register(modEventBus);
        ABILITY_TYPES.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        ATTACHMENT_TYPES.register(modEventBus);
        modEventBus.addListener(
                KingCrimsonAddon::registerPayloads);
        modEventBus.addListener(
                KingCrimsonGameEvents::registerAttributes);
        // 1.16 KCConfig: COMMON, rotp_kingcrimson.toml
        modContainer.registerConfig(
                ModConfig.Type.COMMON,
                KingCrimsonConfig.SPEC,
                KingCrimsonConfig.FILE_NAME);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static EpitaphState epitaphState(Player player) {
        return player.getData(EPITAPH_STATE);
    }

    public static KingCrimsonTemporalState temporalState(
            Player player) {
        return player.getData(TEMPORAL_STATE);
    }

    public static boolean isKingCrimsonUser(Player player) {
        StandPower power = StandPower.get(player);
        return power != null
                && power.getPowerType() == KING_CRIMSON.get();
    }

    public static void syncEpitaphState(Player player) {
        if (player.level().isClientSide()) {
            return;
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                player,
                EpitaphStateSyncPayload.from(
                        player, epitaphState(player)));
    }

    public static void syncEpitaphState(
            ServerPlayer observer, Player target) {
        PacketDistributor.sendToPlayer(
                observer,
                EpitaphStateSyncPayload.from(
                        target, epitaphState(target)));
    }

    public static void syncTemporalState(Player player) {
        if (player.level().isClientSide()) {
            return;
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                player,
                TemporalStateSyncPayload.from(
                        player, temporalState(player)));
    }

    public static void syncTemporalState(
            ServerPlayer observer, Player target) {
        PacketDistributor.sendToPlayer(
                observer,
                TemporalStateSyncPayload.from(
                        target, temporalState(target)));
    }

    private static void registerPayloads(
            RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(
                EpitaphStateSyncPayload.TYPE,
                EpitaphStateSyncPayload.STREAM_CODEC,
                EpitaphStateSyncPayload::handle);
        event.registrar("1").playToClient(
                EpitaphForesightPayload.TYPE,
                EpitaphForesightPayload.STREAM_CODEC,
                EpitaphForesightPayload::handle);
        event.registrar("1").playToClient(
                TemporalStateSyncPayload.TYPE,
                TemporalStateSyncPayload.STREAM_CODEC,
                TemporalStateSyncPayload::handle);
        // "2" since R145 dropped its trailing boolean: a port.18 and port.19 pair is refused at login.
        event.registrar("2").playToClient(
                TimeEraseAfterimageSpawnPayload.TYPE,
                TimeEraseAfterimageSpawnPayload.STREAM_CODEC,
                TimeEraseAfterimageSpawnPayload::handle);
        event.registrar("1").playToClient(
                TimeEraseAfterimageRemovePayload.TYPE,
                TimeEraseAfterimageRemovePayload.STREAM_CODEC,
                TimeEraseAfterimageRemovePayload::handle);
        event.registrar("1").playToClient(
                TimeEraseTransitionPayload.TYPE,
                TimeEraseTransitionPayload.STREAM_CODEC,
                TimeEraseTransitionPayload::handle);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUND_EVENTS.register(
                name,
                SoundEvent::createVariableRangeEvent);
    }

    private static EntityStandType createStand(ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addHumanoidStandStuff()
                .addAbility(
                        "punch",
                        ModStandAbilities.PUNCH,
                        punch -> {
                            punch.detachedLightCounterKnockback();
                            // 1.16 StandEntityTask reads PERFORM and RECOVERY when each phase starts
                            punch.laterPhaseEntryTiming();
                            punch.punchImpactSound(KINGCRIMSON_PUNCH);
                            punch.standCrySound(DIAVOLO_PUNCH);
                        })
                .addAbility(
                        "barrage",
                        KING_CRIMSON_BARRAGE_ABILITY,
                        barrage -> {
                            barrage.barrageHitSound(KINGCRIMSON_PUNCH);
                            barrage.barrageCrySound(DIAVOLO_BARRAGE);
                        })
                .addAbility(
                        "kingcrimson_doppio_barrage",
                        KING_CRIMSON_DOPPIO_BARRAGE_ABILITY,
                        barrage -> {
                            barrage.barrageHitSound(KINGCRIMSON_PUNCH);
                            barrage.barrageCrySound(DOPPIO_BARRAGE);
                            // 1.16 standSound(DOPPIO_BARRAGE) also cried with the arms only.
                            barrage.barrageCryInArmsOnly();
                        })
                .addAbility(
                        "heavy_punch",
                        KING_CRIMSON_HEAVY_PUNCH_ABILITY,
                        punch -> {
                            punch.detachedPunchKnockback();
                            punch.recoveryPhaseEntryTiming();
                            punch.partsRequired(StandPart.MAIN_BODY);
                            punch.heavyPunchImpactSound(
                                    KINGCRIMSON_HEAVYPUNCH);
                            punch.heavyPunchCrySound(DIAVOLO_HEAVYPUNCH);
                        })
                .addAbility(
                        "heavy_charged",
                        ModStandAbilities.HEAVY_CHARGED)
                .addAbility("grab", ModStandAbilities.GRAB)
                .addAbility("grab_throw", ModStandAbilities.GRAB_THROW)
                .addAbility(
                        "grab_punch",
                        ModStandAbilities.PUNCH,
                        punch -> {
                            punch.punchImpactSound(KINGCRIMSON_PUNCH);
                            punch.standCrySound(DIAVOLO_PUNCH);
                            punch.initIsGrabVariation();
                        })
                .addAbility(
                        "grab_barrage",
                        ModStandAbilities.BARRAGE,
                        barrage -> {
                            barrage.barrageHitSound(KINGCRIMSON_PUNCH);
                            barrage.barrageCrySound(DIAVOLO_BARRAGE);
                            barrage.initIsGrabVariation();
                        })
                .addAbility(
                        "grab_heavy_punch",
                        ModStandAbilities.HEAVY_PUNCH,
                        punch -> {
                            punch.heavyPunchImpactSound(
                                    KINGCRIMSON_HEAVYPUNCH);
                            punch.heavyPunchCrySound(DIAVOLO_HEAVYPUNCH);
                            punch.initIsGrabVariation();
                        })
                .addAbility("guard", ModStandAbilities.GUARD)
                .addAbility(
                        "kingcrimson_chop",
                        KING_CRIMSON_CHOP_ABILITY,
                        ability -> ability.resolveLevelToUnlock(3))
                .addAbility(
                        "kingcrimson_impale",
                        KING_CRIMSON_IMPALE_ABILITY,
                        ability -> ability.resolveLevelToUnlock(4))
                .addAbility(
                        "kingcrimson_projectile_throw",
                        KING_CRIMSON_PROJECTILE_THROW_ABILITY,
                        ability -> ability.resolveLevelToUnlock(3))
                .addAbility(
                        "kingcrimson_projectile_throw_tripple",
                        KING_CRIMSON_PROJECTILE_THROW_TRIPPLE_ABILITY,
                        ability -> ability.resolveLevelToUnlock(3))
                .addAbility(
                        "kingcrimson_epitaph",
                        KING_CRIMSON_EPITAPH_ABILITY,
                        ability -> ability.resolveLevelToUnlock(1))
                .addAbility(
                        "kingcrimson_groundpunch",
                        KING_CRIMSON_GROUND_PUNCH_ABILITY)
                .addAbility(
                        "kingcrimson_groundslam",
                        KING_CRIMSON_GROUND_SLAM_ABILITY)
                .addAbility(
                        "kingcrimson_desperate_eyejab",
                        KING_CRIMSON_DESPERATE_EYEJAB_ABILITY)
                .addAbility(
                        "kingcrimson_timeskip",
                        KING_CRIMSON_TIME_SKIP_ABILITY,
                        ability -> ability.resolveLevelToUnlock(3))
                .addAbility(
                        "kingcrimson_timeerase",
                        KING_CRIMSON_TIME_ERASE_ABILITY,
                        ability -> ability.resolveLevelToUnlock(3))
                .makeControlScheme("hotbar")
                    .bind("punch", InputMethod.CLICK, InputKey.LMB)
                    .bind("barrage", InputMethod.HOLD, InputKey.LMB)
                    .bind("heavy_punch", InputMethod.CLICK, InputKey.RMB)
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
                    .bind("guard", InputMethod.HOLD, InputKey.RMB)
                    .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
                    .addToHotbar(
                            "kingcrimson_chop",
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            "kingcrimson_impale",
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            "kingcrimson_projectile_throw",
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            "kingcrimson_projectile_throw_tripple",
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            "kingcrimson_epitaph",
                            0,
                            InputMethod.HOLD)
                    .addToHotbar(
                            "kingcrimson_timeskip",
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            "kingcrimson_timeerase",
                            0,
                            InputMethod.HOLD)
                .finalizeControlScheme()
                .makeControlScheme("keybinds")
                    .bind("punch", InputMethod.CLICK, InputKey.LMB)
                    .bind("barrage", InputMethod.HOLD, InputKey.LMB)
                    .bind("heavy_punch", InputMethod.CLICK, InputKey.RMB)
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
                    .bind("guard", InputMethod.HOLD, InputKey.RMB)
                .finalizeControlScheme()
                .addSkill(StandUnlockableSkill.startingAbility("punch"))
                .addSkill(StandUnlockableSkill.startingAbility("barrage")
                        .withAbility(
                                "kingcrimson_doppio_barrage"))
                .addSkill(StandUnlockableSkill.startingAbility(
                                "heavy_punch")
                        .withAbility(
                                "kingcrimson_groundpunch",
                                "kingcrimson_groundslam",
                                "kingcrimson_desperate_eyejab"))
                .addSkill(StandUnlockableSkill.startingAbility(
                                "heavy_charged")
                        .prerequisiteSkill("heavy_punch"))
                .addSkill(StandUnlockableSkill.startingAbility("guard"))
                .addSkill(StandUnlockableSkill.startingAbility("grab"))
                .addSkill(StandUnlockableSkill.unlockableAbility(
                                "grab_throw",
                                100)
                        .withAbility(
                                "grab_punch",
                                "grab_barrage",
                                "grab_heavy_punch")
                        .prerequisiteSkill("grab"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "manual_control"));
        moveset
                .addSkill(StandUnlockableSkill.unlockableAbility(
                                "kingcrimson_chop",
                                (int) KingCrimsonPhase2Spec.CHOP_STAMINA)
                        .withAbility(
                                "kingcrimson_projectile_throw",
                                "kingcrimson_projectile_throw_tripple")
                        .prerequisiteSkill("heavy_punch"))
                .addSkill(StandUnlockableSkill.unlockableAbility(
                                "kingcrimson_impale",
                                (int) KingCrimsonPhase2Spec.IMPALE_STAMINA)
                        .prerequisiteSkill("kingcrimson_chop"))
                .addSkill(StandUnlockableSkill.unlockableAbility(
                                "kingcrimson_epitaph",
                                (int) KingCrimsonPhase3Spec
                                        .EPITAPH_ACTIVATION_STAMINA)
                        .prerequisiteSkill("guard"))
                .addSkill(StandUnlockableSkill.unlockableAbility(
                                "kingcrimson_timeskip",
                                (int) KingCrimsonPhase5Spec
                                        .TIME_SKIP_STAMINA)
                        .prerequisiteSkill("kingcrimson_epitaph"))
                .addSkill(StandUnlockableSkill.unlockableAbility(
                                "kingcrimson_timeerase",
                                (int) KingCrimsonPhase5Spec
                                        .TIME_ERASE_ACTIVATION_STAMINA)
                        .prerequisiteSkill("kingcrimson_timeskip"));

        return new EntityStandType(
                new StandStats.Builder()
                        .power(KingCrimsonPhase1Spec.POWER)
                        .speed(KingCrimsonPhase1Spec.SPEED)
                        .range(
                                KingCrimsonPhase1Spec.EFFECTIVE_RANGE,
                                KingCrimsonPhase1Spec.MAX_RANGE)
                        .durability(KingCrimsonPhase1Spec.DURABILITY)
                        .precision(KingCrimsonPhase1Spec.PRECISION)
                        .randomWeight(1.0D)
                        .build(),
                moveset,
                KING_CRIMSON_ENTITY.get(),
                id)
                .standControlType(StandControlType.CLOSE_RANGE_DIRECT)
                .manualControl(true)
                .distanceStrengthDecay(true)
                .standDimensions(
                        KingCrimsonPhase1Spec.ENTITY_WIDTH,
                        KingCrimsonPhase1Spec.ENTITY_HEIGHT)
                .summonShout(() -> DIAVOLO_KINGCRIMSON)
                .summonSound(() -> KINGCRIMSON_SUMMON)
                .unsummonSound(() -> KINGCRIMSON_UNSUMMON)
                .ost(() -> KINGCRIMSON_OST);
    }
}
