package rotp.addons.d4c;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import rotp.core.api.RotpAddonApi;
import rotp.core.api.stand.StandSkillWalls;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModItems;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;

import rotp.addons.d4c.ability.D4CBarrageAbility;
import rotp.addons.d4c.ability.D4CCorpseFinderAbility;
import rotp.addons.d4c.ability.D4CDimensionSelectorAbility;
import rotp.addons.d4c.ability.D4CParallelWorldTravelAbility;
import rotp.addons.d4c.ability.D4CPhase2BAbility;
import rotp.addons.d4c.ability.D4CPunchAbility;
import rotp.addons.d4c.ability.D4CPhase2BAbility.Mode;
import rotp.addons.d4c.ability.D4CSaintCorpseXrayAbility;
import rotp.addons.d4c.ability.D4CSummonKickAbility;
import rotp.addons.d4c.block.D4CPortalBlock;
import rotp.addons.d4c.effect.D4CMobEffect;
import rotp.addons.d4c.entity.D4CAnnihilationCloneEntity;
import rotp.addons.d4c.entity.D4CColtBulletEntity;
import rotp.addons.d4c.entity.D4CMarkerEntity;
import rotp.addons.d4c.entity.D4CMinionEntity;
import rotp.addons.d4c.entity.D4CSummonKickEntity;
import rotp.addons.d4c.item.D4CColtItem;
import rotp.addons.d4c.item.SaintCorpsePartItem;
import rotp.addons.d4c.network.D4CPayloads;
import rotp.addons.d4c.travel.D4CDimensionTargets;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(D4CAddon.MOD_ID)
public final class D4CAddon {
    public static final String MOD_ID = "rotp_d4c";
    public static final String PARALLEL_WORLD_TRAVEL_NAME =
            "parallel_world_travel";
    public static final String DIMENSION_SELECTOR_NAME =
            "dimension_selector";
    public static final String SAINT_CORPSE_XRAY_NAME =
            "saint_corpse_xray";
    public static final String CORPSE_FINDER_NAME =
            "corpse_finder";
    private static final int REQUIRED_CORE_ABI = 1;

    public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES =
            DeferredRegister.create(JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, MOD_ID);
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, MOD_ID);
    public static final DeferredRegister<StandType> STAND_TYPES =
            DeferredRegister.create(JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(MOD_ID);

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<D4CParallelWorldTravelAbility>>
            PARALLEL_WORLD_TRAVEL = ABILITY_TYPES.register(
                    PARALLEL_WORLD_TRAVEL_NAME,
                    key -> new AbilityType<>(
                            key, D4CParallelWorldTravelAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<D4CDimensionSelectorAbility>>
            DIMENSION_SELECTOR = ABILITY_TYPES.register(
                    DIMENSION_SELECTOR_NAME,
                    key -> new AbilityType<>(
                            key, D4CDimensionSelectorAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<D4CSaintCorpseXrayAbility>>
            SAINT_CORPSE_XRAY = ABILITY_TYPES.register(
                    SAINT_CORPSE_XRAY_NAME,
                    key -> new AbilityType<>(
                            key, D4CSaintCorpseXrayAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<D4CCorpseFinderAbility>>
            CORPSE_FINDER = ABILITY_TYPES.register(
                    CORPSE_FINDER_NAME,
                    key -> new AbilityType<>(
                            key, D4CCorpseFinderAbility::new));
    // Core barrage plus the 1.16 valentine_barrage shout.
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<D4CBarrageAbility>>
            D4C_STAND_BARRAGE = ABILITY_TYPES.register(
                    "d4c_stand_barrage",
                    key -> new AbilityType<>(
                            key, D4CBarrageAbility::new));
    // Core punch plus the 1.16 valentine_punch user shout.
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<D4CPunchAbility>>
            D4C_PUNCH_TYPE = ABILITY_TYPES.register(
                    "d4c_punch",
                    key -> new AbilityType<>(
                            key, D4CPunchAbility::new));

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<D4CPhase2BAbility>>
            D4C_RAPID_PUNCH = phase2b("d4c_rapid_punch", Mode.RAPID_PUNCH);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<D4CPhase2BAbility>>
            D4C_HAND_CHOP = phase2b("d4c_hand_chop", Mode.HAND_CHOP);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<D4CPhase2BAbility>>
            SUMMON_PLAYER_MINION =
                    phase2b("summon_player_minion", Mode.SUMMON_MINION);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<D4CPhase2BAbility>>
            ANNIHILATION_MARK =
                    phase2b("annihilation_mark", Mode.ANNIHILATION_MARK);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<D4CPhase2BAbility>>
            ANNIHILATION = phase2b("annihilation", Mode.ANNIHILATION);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<D4CPhase2BAbility>>
            FLAG_PARALLEL_WORLD_TRAVEL = phase2b(
                    "flag_parallel_world_travel", Mode.FLAG_TRAVEL);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<D4CPhase2BAbility>>
            SWAP_WITH_MINION =
                    phase2b("swap_with_minion", Mode.SWAP_WITH_MINION);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<D4CPhase2BAbility>>
            LOVE_TRAIN_ABILITY = phase2b("love_train", Mode.LOVE_TRAIN);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<D4CPhase2BAbility>>
            LOVE_TRAIN_DEACTIVATE_ABILITY = phase2b(
                    "love_train_deactivate", Mode.LOVE_TRAIN_DEACTIVATE);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<D4CPhase2BAbility>>
            LOVE_TRAIN_LIGHT_WALL_ABILITY =
                    phase2b("love_train_light_wall", Mode.LIGHT_WALL);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<D4CPhase2BAbility>>
            LOVE_TRAIN_LIGHT_WALL_DEACTIVATE_ABILITY = phase2b(
                    "love_train_light_wall_deactivate",
                    Mode.LIGHT_WALL_DEACTIVATE);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<D4CPhase2BAbility>>
            MINION_AI_CONTROL =
                    phase2b("minion_ai_control", Mode.MINION_AI_CONTROL);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<D4CPhase2BAbility>>
            PULL_IN_PARALLEL_WORLD_TRAVEL = phase2b(
                    "pullin_parallel_world_travel", Mode.PULL_IN_TRAVEL);
    // 1.16 summon_kick was a plain StandAction, not a Stand task.
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<D4CSummonKickAbility>>
            SUMMON_KICK = ABILITY_TYPES.register(
                    "summon_kick",
                    key -> new AbilityType<>(
                            key, D4CSummonKickAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<D4CPhase2BAbility>>
            D4C_ITEM_THROW =
                    phase2b("d4c_item_throw", Mode.ITEM_THROW);

    public static final DeferredHolder<EntityType<?>, EntityType<StandEntity>>
            D4C_ENTITY = ENTITY_TYPES.register(
                    "d4c",
                    key -> EntityType.Builder.<StandEntity>of(
                                    StandEntity::new, MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(
                                    D4CPhase1Spec.ENTITY_WIDTH,
                                    D4CPhase1Spec.ENTITY_HEIGHT)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<
            EntityType<?>, EntityType<D4CMinionEntity>>
            SUMMONED_PLAYER = ENTITY_TYPES.register(
                    "summoned_player",
                    key -> EntityType.Builder
                            .<D4CMinionEntity>of(
                                    D4CMinionEntity::new,
                                    MobCategory.CREATURE)
                            .sized(0.6F, 1.8F)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));
    public static final DeferredHolder<
            EntityType<?>, EntityType<D4CAnnihilationCloneEntity>>
            ANNIHILATION_CLONE = annihilationClone("annihilation_clone");
    public static final DeferredHolder<
            EntityType<?>, EntityType<D4CAnnihilationCloneEntity>>
            ANNIHILATION_PLAYER_CLONE =
                    annihilationClone("annihilation_player_clone");
    public static final DeferredHolder<
            EntityType<?>, EntityType<D4CColtBulletEntity>>
            COLT_BULLET = ENTITY_TYPES.register(
                    "colt_bullet",
                    key -> EntityType.Builder
                            .<D4CColtBulletEntity>of(
                                    D4CColtBulletEntity::new,
                                    MobCategory.MISC)
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(64)
                            .updateInterval(1)
                            .build(key.toString()));
    public static final DeferredHolder<
            EntityType<?>, EntityType<D4CMarkerEntity>>
            FLAG = marker("flag", 1.0F, 2.0F);
    public static final DeferredHolder<
            EntityType<?>, EntityType<D4CSummonKickEntity>>
            SUMMON_KICK_ENTITY = ENTITY_TYPES.register(
                    "summon_kick",
                    key -> EntityType.Builder
                            .<D4CSummonKickEntity>of(
                                    D4CSummonKickEntity::new,
                                    MobCategory.CREATURE)
                            .sized(0.6F, 1.8F)
                            .clientTrackingRange(64)
                            .updateInterval(1)
                            .build(key.toString()));
    public static final DeferredHolder<
            EntityType<?>, EntityType<D4CMarkerEntity>>
            SUMMON_KICK_FLAG = marker("summon_kick_flag", 1.0F, 2.0F);

    public static final DeferredBlock<D4CPortalBlock>
            PARALLEL_WORLD_PORTAL = portal(
                    "parallel_world_portal",
                    D4CDimensionTargets.PARALLEL_WORLD);
    public static final DeferredBlock<D4CPortalBlock>
            NETHER_PORTAL = portal(
                    "nether_portal",
                    D4CDimensionTargets.NETHER);
    public static final DeferredBlock<D4CPortalBlock>
            END_PORTAL = portal(
                    "end_portal",
                    D4CDimensionTargets.END);
    public static final DeferredBlock<D4CPortalBlock>
            OVERWORLD_PORTAL = portal(
                    "overworld_portal",
                    D4CDimensionTargets.OVERWORLD);
    public static final DeferredBlock<D4CPortalBlock>
            ATUM_PORTAL = portal(
                    "atum_portal",
                    D4CDimensionTargets.LEGACY_ORDER.get(4));
    public static final DeferredBlock<D4CPortalBlock>
            DEEP_DARK_PORTAL = portal(
                    "deep_dark_portal",
                    D4CDimensionTargets.LEGACY_ORDER.get(5));
    public static final DeferredBlock<D4CPortalBlock>
            LOST_CITY_PORTAL = portal(
                    "lost_city_portal",
                    D4CDimensionTargets.LEGACY_ORDER.get(6));
    public static final DeferredBlock<D4CPortalBlock>
            SKYLIGHT_FOREST_PORTAL = portal(
                    "skylight_forest_portal",
                    D4CDimensionTargets.LEGACY_ORDER.get(7));
    public static final DeferredBlock<D4CPortalBlock>
            TWILIGHT_FOREST_PORTAL = portal(
                    "twilight_forest_portal",
                    D4CDimensionTargets.LEGACY_ORDER.get(8));

    public static final DeferredHolder<Item, SaintCorpseHeartItem>
            SAINT_CORPSE_HEART = ITEMS.register(
                    "saint_corpse_heart",
                    () -> new SaintCorpseHeartItem(
                            new Item.Properties()
                                    .stacksTo(1)
                                    .rarity(Rarity.EPIC)));
    public static final DeferredHolder<Item, D4CColtItem> COLT_CUSTOM =
            ITEMS.register(
                    "colt_custom",
                    () -> new D4CColtItem(
                            new Item.Properties()
                                    .stacksTo(1)
                                    .rarity(Rarity.UNCOMMON)));
    public static final DeferredHolder<Item, SaintCorpsePartItem>
            SAINT_CORPSE_EARS = corpsePart("saint_corpse_ears");
    public static final DeferredHolder<Item, SaintCorpsePartItem>
            SAINT_CORPSE_EYES = corpsePart("saint_corpse_eyes");
    public static final DeferredHolder<Item, SaintCorpsePartItem>
            SAINT_CORPSE_HEAD = corpsePart("saint_corpse_head");
    public static final DeferredHolder<Item, SaintCorpsePartItem>
            SAINT_CORPSE_LEFT_ARM = corpsePart("saint_corpse_left_arm");
    public static final DeferredHolder<Item, SaintCorpsePartItem>
            SAINT_CORPSE_RIGHT_ARM = corpsePart("saint_corpse_right_arm");
    public static final DeferredHolder<Item, SaintCorpsePartItem>
            SAINT_CORPSE_LEGS = corpsePart("saint_corpse_legs");
    public static final DeferredHolder<Item, SaintCorpsePartItem>
            SAINT_CORPSE_THORAX = corpsePart("saint_corpse_thorax");
    public static final DeferredHolder<Item, SaintCorpsePartItem>
            SAINT_CORPSE_SPINE = corpsePart("saint_corpse_spine");
    public static final DeferredHolder<Item, SaintCorpsePartItem>
            COMPLETE_SAINT_CORPSE = corpsePart("complete_saint_corpse");

    public static final DeferredHolder<MobEffect, D4CMobEffect> LOVE_TRAIN =
            effect(
                    "love_train",
                    MobEffectCategory.BENEFICIAL,
                    0xF6D365);
    public static final DeferredHolder<MobEffect, D4CMobEffect>
            LUCKY_GODDESS = effect(
                    "lucky_goddess",
                    MobEffectCategory.BENEFICIAL,
                    0xFFF4A3);
    public static final DeferredHolder<MobEffect, D4CMobEffect>
            LOVE_TRAIN_LIGHT_WALL = effect(
                    "love_train_light_wall",
                    MobEffectCategory.BENEFICIAL,
                    0xFFF8D2);
    public static final DeferredHolder<MobEffect, D4CMobEffect> SANDSTORM =
            effect(
                    "sandstorm",
                    MobEffectCategory.HARMFUL,
                    0xC9A46C);
    public static final DeferredHolder<MobEffect, D4CMobEffect> BLEEDING =
            effect(
                    "bleeding",
                    MobEffectCategory.HARMFUL,
                    0x8A1010);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType>
            GOLDEN_LIGHT_PILLAR = particle("golden_light_pillar");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType>
            SHORT_GOLDEN_LIGHT_PILLAR =
                    particle("short_golden_light_pillar");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType>
            ANNIHILATE = particle("annihilate");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType>
            LIGHT_BALL = particle("light_ball");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType>
            WIDE_GOLDEN_LIGHT_PILLAR =
                    particle("wide_golden_light_pillar");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType>
            GOLD_LIGHT = particle("gold_light");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType>
            DEVIL_SKY = particle("devil_sky");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType>
            SKULLHEAD = particle("skullhead");

    public static final DeferredHolder<SoundEvent, SoundEvent>
            D4C_SUMMON = sound("d4c_summon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            D4C_UNSUMMON = sound("d4c_unsummon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            D4C_PUNCH = sound("d4c_punch");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            D4C_BARRAGE = sound("d4c_barrage");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            D4C_PUNCH_HEAVY = sound("d4c_punch_heavy");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            VALENTINE_D4C = sound("valentine_d4c");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            VALENTINE_PUNCH = sound("valentine_punch");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            VALENTINE_RAIDPUNCH = sound("valentine_raidpunch");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            VALENTINE_HEAVY = sound("valentine_heavy");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            D4C_TRAVERSE = sound("d4c_traverse");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            D4C_TRAVERSE_ON = sound("d4c_traverseon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            VALENTINE_TRAVEL = sound("valentine_travel");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            D4C_LOVE_TRAIN_SUMMON = sound("d4c_love_train_summon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            D4C_TELEPORT = sound("d4c_teleport");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            D4C_FLANG = sound("d4c_flang");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            D4C_LIGHT = sound("d4c_light");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            D4C_HANDCHOP = sound("d4c_handchop");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            D4C_USHYA = sound("d4c_ushya");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            D4C_TRAVERSE_FANG = sound("d4c_traversefang");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            D4C_ANNIHILATE = sound("d4c_annihilate");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            VALENTINE_LOVE = sound("valentine_love");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            VALENTINE_LOVE_TRAIN = sound("valentine_love_train");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            VALENTINE_ANNIHILATION = sound("valentine_annihilation");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            VALENTINE_CHANGE = sound("valentine_change");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            VALENTINE_PULL_TRAVEL = sound("valentine_pulltravel");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            VALENTINE_SUMMON_ME = sound("valentine_summonme");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            VALENTINE_BARRAGE = sound("valentine_barrage");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            D4C_MELT_HEART = sound("d4c_melt_heart");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            COLT_SHOOT = sound("colt_shoot");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            COLT_RELOAD = sound("colt_reload");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            COLT_RELOAD_ON = sound("colt_reloadon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            CORPSE_FINDER_GUIDE = sound("corpse_finder_guide");

    public static final DeferredHolder<StandType, EntityStandType> D4C =
            STAND_TYPES.register("d4c", D4CAddon::createStand);
    public static final String ANNIHILATION_MARK_NAME = "annihilation_mark";

    public D4CAddon(IEventBus modEventBus) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_VISUAL_CONTEXT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_RANDOM_WEIGHTS_V1)
                // 1.16 action shouts follow the sneak rule
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_SHOUTS_V1)) {
            throw new IllegalStateException(
                    "RotP D4C requires ROTP addon ABI 1 with namespaced "
                            + "ability resources, stand visual contexts, "
                            + "and Stand random weights.");
        }
        if (!RotpAddonApi.supportsFeature(
                RotpAddonApi.FEATURE_TIME_STOP_LIFECYCLE_V1)) {
            throw new IllegalStateException(
                    "RotP D4C Phase 2A requires the public time-stop "
                            + "lifecycle contract.");
        }
        if (!RotpAddonApi.supportsFeature(
                RotpAddonApi.FEATURE_STAND_POWER_TRANSITIONS_V3)) {
            throw new IllegalStateException(
                    "RotP D4C requires the public contextual Stand "
                            + "transition and mutation veto contract.");
        }
        // the rapid punch and the hand chop refresh their front offset there
        if (!RotpAddonApi.supportsFeature(
                RotpAddonApi.FEATURE_ACTION_STAND_OFFSET_HOOK_V1)) {
            throw new IllegalStateException(
                    "RotP D4C requires the Stand action offset hook.");
        }

        ABILITY_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        MOB_EFFECTS.register(modEventBus);
        PARTICLE_TYPES.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::addCreativeTabContents);
        modEventBus.addListener(D4CPayloads::register);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUND_EVENTS.register(
                name,
                SoundEvent::createVariableRangeEvent);
    }

    private static DeferredHolder<
            AbilityType<?>, AbilityType<D4CPhase2BAbility>>
            phase2b(String name, Mode mode) {
        return ABILITY_TYPES.register(
                name,
                key -> new AbilityType<>(
                        key,
                        (type, id) ->
                                new D4CPhase2BAbility(type, id, mode)));
    }

    private static DeferredHolder<
            EntityType<?>, EntityType<D4CAnnihilationCloneEntity>>
            annihilationClone(String name) {
        return ENTITY_TYPES.register(
                name,
                key -> EntityType.Builder
                        .<D4CAnnihilationCloneEntity>of(
                                D4CAnnihilationCloneEntity::new,
                                MobCategory.CREATURE)
                        .sized(0.6F, 1.8F)
                        .clientTrackingRange(32)
                        .updateInterval(2)
                        .build(key.toString()));
    }

    private static DeferredHolder<
            EntityType<?>, EntityType<D4CMarkerEntity>>
            marker(String name, float width, float height) {
        return ENTITY_TYPES.register(
                name,
                key -> EntityType.Builder
                        .<D4CMarkerEntity>of(
                                D4CMarkerEntity::new,
                                MobCategory.MISC)
                        .noSave()
                        .sized(width, height)
                        .clientTrackingRange(64)
                        .updateInterval(1)
                        .build(key.toString()));
    }

    private static DeferredHolder<Item, SaintCorpsePartItem> corpsePart(
            String name) {
        return ITEMS.register(
                name,
                () -> new SaintCorpsePartItem(
                        new Item.Properties()
                                .stacksTo(1)
                                .rarity(Rarity.EPIC)));
    }

    private static DeferredBlock<D4CPortalBlock> portal(
            String name,
            ResourceLocation targetDimension) {
        return BLOCKS.register(
                name,
                () -> new D4CPortalBlock(
                        BlockBehaviour.Properties.of()
                                .noCollission()
                                .noOcclusion()
                                .replaceable()
                                .instabreak()
                                .noLootTable()
                                .lightLevel(state -> 8),
                        targetDimension));
    }

    public static D4CPortalBlock portalBlockFor(
            ResourceLocation targetDimension) {
        if (D4CDimensionTargets.NETHER.equals(targetDimension)) {
            return NETHER_PORTAL.get();
        }
        if (D4CDimensionTargets.END.equals(targetDimension)) {
            return END_PORTAL.get();
        }
        if (D4CDimensionTargets.PARALLEL_WORLD.equals(
                targetDimension)) {
            return PARALLEL_WORLD_PORTAL.get();
        }
        if (D4CDimensionTargets.LEGACY_ORDER.get(4).equals(
                targetDimension)) {
            return ATUM_PORTAL.get();
        }
        if (D4CDimensionTargets.LEGACY_ORDER.get(5).equals(
                targetDimension)) {
            return DEEP_DARK_PORTAL.get();
        }
        if (D4CDimensionTargets.LEGACY_ORDER.get(6).equals(
                targetDimension)) {
            return LOST_CITY_PORTAL.get();
        }
        if (D4CDimensionTargets.LEGACY_ORDER.get(7).equals(
                targetDimension)) {
            return SKYLIGHT_FOREST_PORTAL.get();
        }
        if (D4CDimensionTargets.LEGACY_ORDER.get(8).equals(
                targetDimension)) {
            return TWILIGHT_FOREST_PORTAL.get();
        }
        return OVERWORLD_PORTAL.get();
    }

    private static DeferredHolder<MobEffect, D4CMobEffect> effect(
            String name, MobEffectCategory category, int color) {
        return MOB_EFFECTS.register(
                name, () -> new D4CMobEffect(category, color));
    }

    private static DeferredHolder<ParticleType<?>, SimpleParticleType>
            particle(String name) {
        return PARTICLE_TYPES.register(
                name, () -> new SimpleParticleType(false));
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            D4CStandTransitionVetoes.register();
            D4CCorpseService.registerStaminaModifiers();
            D4CCuriosAdapter.registerSaintCorpseItems(
                    SAINT_CORPSE_EARS.get(),
                    SAINT_CORPSE_EYES.get(),
                    SAINT_CORPSE_HEAD.get(),
                    SAINT_CORPSE_HEART.get(),
                    SAINT_CORPSE_LEFT_ARM.get(),
                    SAINT_CORPSE_RIGHT_ARM.get(),
                    SAINT_CORPSE_LEGS.get(),
                    SAINT_CORPSE_THORAX.get(),
                    SAINT_CORPSE_SPINE.get(),
                    COMPLETE_SAINT_CORPSE.get());
        });
        // The Annihilation mark is laid on its target, behind walls too.
        StandSkillWalls.markThroughWalls(D4C.getId(), ANNIHILATION_MARK_NAME);
    }

    private void addCreativeTabContents(
            BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == ModItems.MAIN_TAB.getKey()) {
            event.accept(SAINT_CORPSE_HEART.get());
            event.accept(COLT_CUSTOM.get());
            event.accept(SAINT_CORPSE_EARS.get());
            event.accept(SAINT_CORPSE_EYES.get());
            event.accept(SAINT_CORPSE_HEAD.get());
            event.accept(SAINT_CORPSE_LEFT_ARM.get());
            event.accept(SAINT_CORPSE_RIGHT_ARM.get());
            event.accept(SAINT_CORPSE_LEGS.get());
            event.accept(SAINT_CORPSE_THORAX.get());
            event.accept(SAINT_CORPSE_SPINE.get());
            event.accept(COMPLETE_SAINT_CORPSE.get());
        }
    }

    private static EntityStandType createStand(ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addHumanoidStandStuff()
                .addAbility(
                        "d4c_punch",
                        D4C_PUNCH_TYPE,
                        punch -> {
                            punch.punchImpactSound(D4C_PUNCH);
                            // 1.16 valentine_punch is the user shout
                        })
                .addAbility(
                        "d4c_barrage",
                        D4C_STAND_BARRAGE,
                        barrage -> {
                            barrage.barrageHitSound(D4C_BARRAGE);
                            // 1.16 standSound(d4c_ushya); the user shout is in D4CBarrageAbility
                            barrage.barrageCrySound(D4C_USHYA);
                            // the one-argument standSound also cried with the arms only
                            barrage.barrageCryInArmsOnly();
                        })
                .addAbility(
                        "d4c_heavy_punch",
                        ModStandAbilities.HEAVY_PUNCH,
                        punch -> {
                            punch.heavyPunchImpactSound(D4C_PUNCH_HEAVY);
                            punch.heavyPunchCrySound(VALENTINE_HEAVY);
                        })
                .addAbility(
                        "heavy_charged",
                        ModStandAbilities.HEAVY_CHARGED)
                .addAbility("grab", ModStandAbilities.GRAB)
                .addAbility("grab_throw", ModStandAbilities.GRAB_THROW)
                .addAbility(
                        "grab_punch",
                        D4C_PUNCH_TYPE,
                        punch -> {
                            punch.punchImpactSound(D4C_PUNCH);
                            // valentine_punch is the user shout, as for d4c_punch
                            punch.initIsGrabVariation();
                        })
                .addAbility(
                        "grab_barrage",
                        D4C_STAND_BARRAGE,
                        barrage -> {
                            barrage.barrageHitSound(D4C_BARRAGE);
                            barrage.barrageCrySound(D4C_USHYA);
                            barrage.initIsGrabVariation();
                        })
                .addAbility(
                        "grab_heavy_punch",
                        ModStandAbilities.HEAVY_PUNCH,
                        punch -> {
                            punch.heavyPunchImpactSound(D4C_PUNCH_HEAVY);
                            punch.heavyPunchCrySound(VALENTINE_HEAVY);
                            punch.initIsGrabVariation();
                        })
                .addAbility("d4c_block", ModStandAbilities.GUARD)
                .addAbility(
                        PARALLEL_WORLD_TRAVEL_NAME,
                        PARALLEL_WORLD_TRAVEL)
                .addAbility(DIMENSION_SELECTOR_NAME, DIMENSION_SELECTOR)
                .addAbility("d4c_rapid_punch", D4C_RAPID_PUNCH)
                .addAbility("d4c_hand_chop", D4C_HAND_CHOP)
                .addAbility("summon_player_minion", SUMMON_PLAYER_MINION)
                .addAbility("annihilation_mark", ANNIHILATION_MARK)
                .addAbility("annihilation", ANNIHILATION)
                .addAbility(
                        "flag_parallel_world_travel",
                        FLAG_PARALLEL_WORLD_TRAVEL)
                .addAbility("swap_with_minion", SWAP_WITH_MINION)
                .addAbility("love_train", LOVE_TRAIN_ABILITY)
                .addAbility(
                        "love_train_deactivate",
                        LOVE_TRAIN_DEACTIVATE_ABILITY)
                .addAbility(
                        "love_train_light_wall",
                        LOVE_TRAIN_LIGHT_WALL_ABILITY)
                .addAbility(
                        "love_train_light_wall_deactivate",
                        LOVE_TRAIN_LIGHT_WALL_DEACTIVATE_ABILITY)
                .addAbility("minion_ai_control", MINION_AI_CONTROL)
                .addAbility(
                        "pullin_parallel_world_travel",
                        PULL_IN_PARALLEL_WORLD_TRAVEL)
                .addAbility("summon_kick", SUMMON_KICK)
                .addAbility("d4c_item_throw", D4C_ITEM_THROW)
                .addAbility(SAINT_CORPSE_XRAY_NAME, SAINT_CORPSE_XRAY)
                .addAbility(CORPSE_FINDER_NAME, CORPSE_FINDER)
                .makeControlScheme("hotbar")
                    .bind("d4c_punch", InputMethod.CLICK, InputKey.LMB)
                    .bind("d4c_barrage", InputMethod.HOLD, InputKey.LMB)
                    .bind(
                            "d4c_heavy_punch",
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
                    .bind("d4c_block", InputMethod.HOLD, InputKey.RMB)
                    .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
                    .addToHotbar(
                            PARALLEL_WORLD_TRAVEL_NAME,
                            0,
                            InputMethod.HOLD)
                    .addToHotbar(
                            DIMENSION_SELECTOR_NAME,
                            0,
                            InputMethod.CLICK)
                    .addToHotbar("d4c_rapid_punch", 0, InputMethod.CLICK)
                    .addToHotbar(
                            "summon_player_minion",
                            0,
                            InputMethod.HOLD)
                    .addToHotbar(
                            "annihilation_mark",
                            0,
                            InputMethod.CLICK)
                    .addToHotbar("annihilation", 0, InputMethod.HOLD)
                    .addToHotbar(
                            "flag_parallel_world_travel",
                            0,
                            InputMethod.HOLD)
                    .addToHotbar(
                            "swap_with_minion",
                            0,
                            InputMethod.CLICK)
                    .addToHotbar("love_train", 0, InputMethod.HOLD)
                    .addToHotbar(
                            "love_train_deactivate",
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            "love_train_light_wall",
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            "love_train_light_wall_deactivate",
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            "minion_ai_control",
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            "pullin_parallel_world_travel",
                            0,
                            InputMethod.HOLD)
                    .addToHotbar("summon_kick", 0, InputMethod.CLICK)
                    .addToHotbar("d4c_item_throw", 0, InputMethod.CLICK)
                    .addToHotbar(
                            SAINT_CORPSE_XRAY_NAME,
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            CORPSE_FINDER_NAME,
                            0,
                            InputMethod.CLICK)
                .finalizeControlScheme()
                .makeControlScheme("keybinds")
                    .bind("d4c_punch", InputMethod.CLICK, InputKey.LMB)
                    .bind("d4c_barrage", InputMethod.HOLD, InputKey.LMB)
                    .bind(
                            "d4c_heavy_punch",
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
                    .bind("d4c_block", InputMethod.HOLD, InputKey.RMB)
                .finalizeControlScheme()
                .addSkill(StandUnlockableSkill.startingAbility(
                        "d4c_punch"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "d4c_barrage"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "d4c_heavy_punch"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "heavy_charged")
                        .prerequisiteSkill("d4c_heavy_punch"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "d4c_block"))
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
        moveset.addSkill(StandUnlockableSkill.unlockableAbility(
                        PARALLEL_WORLD_TRAVEL_NAME,
                        150)
                .prerequisiteSkill("d4c_heavy_punch"));
        moveset.addSkill(StandUnlockableSkill.unlockableAbility(
                        DIMENSION_SELECTOR_NAME,
                        100)
                .prerequisiteSkill(PARALLEL_WORLD_TRAVEL_NAME));
        for (String ability : new String[] {
                "d4c_rapid_punch",
                "d4c_hand_chop",
                "summon_player_minion",
                "annihilation_mark",
                "annihilation",
                "flag_parallel_world_travel",
                "swap_with_minion",
                "love_train",
                "love_train_deactivate",
                "love_train_light_wall",
                "love_train_light_wall_deactivate",
                "minion_ai_control",
                "pullin_parallel_world_travel",
                "summon_kick",
                "d4c_item_throw",
                SAINT_CORPSE_XRAY_NAME,
                CORPSE_FINDER_NAME}) {
            moveset.addSkill(
                    StandUnlockableSkill.startingAbility(ability));
        }

        return new D4CStandType(
                new StandStats.Builder()
                        .power(D4CPhase1Spec.POWER)
                        .speed(D4CPhase1Spec.SPEED)
                        .range(
                                D4CPhase1Spec.EFFECTIVE_RANGE,
                                D4CPhase1Spec.MAX_RANGE)
                        .durability(D4CPhase1Spec.DURABILITY)
                        .precision(D4CPhase1Spec.PRECISION)
                        .randomWeight(1.0D)
                        .build(),
                moveset,
                D4C_ENTITY.get(),
                id)
                .standControlType(StandControlType.CLOSE_RANGE_DIRECT)
                .manualControl(true)
                .distanceStrengthDecay(true)
                .standDimensions(
                        D4CPhase1Spec.ENTITY_WIDTH,
                        D4CPhase1Spec.ENTITY_HEIGHT)
                .summonShout(() -> VALENTINE_D4C)
                .summonSound(() -> D4C_SUMMON)
                .unsummonSound(() -> D4C_UNSUMMON);
    }
}
