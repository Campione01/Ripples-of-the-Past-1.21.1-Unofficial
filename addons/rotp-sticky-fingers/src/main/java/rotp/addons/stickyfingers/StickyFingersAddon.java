package rotp.addons.stickyfingers;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import rotp.core.api.RotpAddonApi;
import rotp.core.api.control.CarriedPassengerActionHooks;
import rotp.core.core.JojoRegistries;
import rotp.core.entityattachment.custom_effect.EntityCustomEffectType;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.type.StandType;

import rotp.addons.stickyfingers.ability.StickyFingersExtendedPunchAbility;
import rotp.addons.stickyfingers.ability.StickyFingersClimbZipperAbility;
import rotp.addons.stickyfingers.ability.StickyFingersHeavyPunchAbility;
import rotp.addons.stickyfingers.ability.StickyFingersHideInMobAbility;
import rotp.addons.stickyfingers.ability.StickyFingersOpenInnerStorageAbility;
import rotp.addons.stickyfingers.ability.StickyFingersOpenStorageAbility;
import rotp.addons.stickyfingers.ability.StickyFingersPunchAbility;
import rotp.addons.stickyfingers.ability.StickyFingersUnzipPartAbility;
import rotp.addons.stickyfingers.ability.StickyFingersUnzipPunchAbility;
import rotp.addons.stickyfingers.ability.StickyFingersZipTargetWoundsAbility;
import rotp.addons.stickyfingers.ability.StickyFingersZipUserWoundsAbility;
import rotp.addons.stickyfingers.ability.StickyFingersZipperAbility;
import rotp.addons.stickyfingers.block.StickyFingersZipperBlock;
import rotp.addons.stickyfingers.blockentity.StickyFingersZipperBlockEntity;
import rotp.addons.stickyfingers.effect.ZipWoundsEffect;
import rotp.addons.stickyfingers.effect.ZipperEffect;
import rotp.addons.stickyfingers.entity.DetachedBodyPartEntity;
import rotp.addons.stickyfingers.entity.StickyFingersExtendedPunchEntity;
import rotp.addons.stickyfingers.entity.StickyFingersStandEntity;
import rotp.addons.stickyfingers.menu.BlockZipperStorageMenu;
import rotp.addons.stickyfingers.menu.EntityZipperStorageMenu;
import rotp.addons.stickyfingers.network.DetachedBodyControlPayload;
import rotp.addons.stickyfingers.network.DetachedBodyPresentationPayload;
import rotp.addons.stickyfingers.state.DetachedPartType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(StickyFingersAddon.MOD_ID)
public final class StickyFingersAddon {
    public static final String MOD_ID = "rotp_stfn";
    public static final String EXTENDED_PUNCH_NAME =
            "sticky_fingers_extended_punch";
    public static final String ZIPPER_NAME =
            "sticky_fingers_zipper";
    public static final String UNZIP_PUNCH_NAME =
            "sticky_fingers_unzip_punch";
    public static final String UNZIP_HEAD_NAME =
            "sticky_fingers_unzip_head";
    public static final String UNZIP_ARM_NAME =
            "sticky_fingers_unzip_arm";
    public static final String UNZIP_LEG_NAME =
            "sticky_fingers_unzip_leg";
    public static final String ZIP_USER_WOUNDS_NAME =
            "sticky_fingers_zip_user_wounds";
    public static final String ZIP_TARGET_WOUNDS_NAME =
            "sticky_fingers_zip_target_wounds";
    public static final String OPEN_STORAGE_NAME =
            "sticky_fingers_open_storage";
    public static final String OPEN_INNER_STORAGE_NAME =
            "sticky_fingers_open_inner_storage";
    public static final String CLIMB_ZIPPER_NAME =
            "sticky_fingers_climb_zipper";
    public static final String HIDE_IN_MOB_NAME =
            "sticky_fingers_hide_in_mob";
    private static final int REQUIRED_CORE_ABI = 1;

    public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES =
            DeferredRegister.create(JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>>
            BLOCK_ENTITY_TYPES = DeferredRegister.create(
                    Registries.BLOCK_ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<StandType> STAND_TYPES =
            DeferredRegister.create(JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);
    public static final DeferredRegister<EntityCustomEffectType<?>>
            STAND_EFFECT_TYPES = DeferredRegister.create(
                    JojoRegistries.STAND_EFFECTS_REG, MOD_ID);

    // 1.16 punch / heavy punch shouted BRUNO_PUNCH / BRUNO_HEAVY_PUNCH
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<StickyFingersPunchAbility>>
            STICKY_FINGERS_PUNCH_ABILITY = ABILITY_TYPES.register(
                    "sticky_fingers_punch",
                    key -> new AbilityType<>(
                            key, StickyFingersPunchAbility::new));

    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<StickyFingersHeavyPunchAbility>>
            STICKY_FINGERS_HEAVY_PUNCH_ABILITY = ABILITY_TYPES.register(
                    "sticky_fingers_heavy_punch",
                    key -> new AbilityType<>(
                            key, StickyFingersHeavyPunchAbility::new));

    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<StickyFingersExtendedPunchAbility>>
            STICKY_FINGERS_EXTENDED_PUNCH = ABILITY_TYPES.register(
                    EXTENDED_PUNCH_NAME,
                    key -> new AbilityType<>(
                            key, StickyFingersExtendedPunchAbility::new));

    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<StickyFingersZipperAbility>>
            STICKY_FINGERS_ZIPPER_ABILITY = ABILITY_TYPES.register(
                    ZIPPER_NAME,
                    key -> new AbilityType<>(
                            key, StickyFingersZipperAbility::new));

    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<StickyFingersUnzipPunchAbility>>
            STICKY_FINGERS_UNZIP_PUNCH = ABILITY_TYPES.register(
                    UNZIP_PUNCH_NAME,
                    key -> new AbilityType<>(
                            key,
                            StickyFingersUnzipPunchAbility::new));

    // 1.16 sticky_fingers_unzip_head / _arm / _leg, the unzip punch's
    // follow-ups (070 InitStands :76-79), and the modifiers they put on it
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<StickyFingersUnzipPartAbility>>
            STICKY_FINGERS_UNZIP_HEAD = unzipPart(
                    UNZIP_HEAD_NAME, DetachedPartType.Region.HEAD);
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<StickyFingersUnzipPartAbility>>
            STICKY_FINGERS_UNZIP_ARM = unzipPart(
                    UNZIP_ARM_NAME, DetachedPartType.Region.TORSO_ARMS);
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<StickyFingersUnzipPartAbility>>
            STICKY_FINGERS_UNZIP_LEG = unzipPart(
                    UNZIP_LEG_NAME, DetachedPartType.Region.LEGS);
    public static final DeferredHolder<
            EntityCustomEffectType<?>,
            EntityCustomEffectType<StickyFingersUnzipPartAbility.UnzipPartModifier>>
            UNZIP_HEAD_MODIFIER = unzipPartModifier(
                    UNZIP_HEAD_NAME, DetachedPartType.Region.HEAD);
    public static final DeferredHolder<
            EntityCustomEffectType<?>,
            EntityCustomEffectType<StickyFingersUnzipPartAbility.UnzipPartModifier>>
            UNZIP_ARM_MODIFIER = unzipPartModifier(
                    UNZIP_ARM_NAME, DetachedPartType.Region.TORSO_ARMS);
    public static final DeferredHolder<
            EntityCustomEffectType<?>,
            EntityCustomEffectType<StickyFingersUnzipPartAbility.UnzipPartModifier>>
            UNZIP_LEG_MODIFIER = unzipPartModifier(
                    UNZIP_LEG_NAME, DetachedPartType.Region.LEGS);

    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<StickyFingersZipUserWoundsAbility>>
            STICKY_FINGERS_ZIP_USER_WOUNDS =
                    ABILITY_TYPES.register(
                            ZIP_USER_WOUNDS_NAME,
                            key -> new AbilityType<>(
                                    key,
                                    StickyFingersZipUserWoundsAbility
                                            ::new));

    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<StickyFingersZipTargetWoundsAbility>>
            STICKY_FINGERS_ZIP_TARGET_WOUNDS =
                    ABILITY_TYPES.register(
                            ZIP_TARGET_WOUNDS_NAME,
                            key -> new AbilityType<>(
                                    key,
                                    StickyFingersZipTargetWoundsAbility
                                            ::new));

    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<StickyFingersOpenStorageAbility>>
            STICKY_FINGERS_OPEN_STORAGE =
                    ABILITY_TYPES.register(
                            OPEN_STORAGE_NAME,
                            key -> new AbilityType<>(
                                    key,
                                    StickyFingersOpenStorageAbility
                                            ::new));

    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<StickyFingersOpenInnerStorageAbility>>
            STICKY_FINGERS_OPEN_INNER_STORAGE =
                    ABILITY_TYPES.register(
                            OPEN_INNER_STORAGE_NAME,
                            key -> new AbilityType<>(
                                    key,
                                    StickyFingersOpenInnerStorageAbility
                                            ::new));

    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<StickyFingersClimbZipperAbility>>
            STICKY_FINGERS_CLIMB_ZIPPER =
                    ABILITY_TYPES.register(
                            CLIMB_ZIPPER_NAME,
                            key -> new AbilityType<>(
                                    key,
                                    StickyFingersClimbZipperAbility
                                            ::new));

    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<StickyFingersHideInMobAbility>>
            STICKY_FINGERS_HIDE_IN_MOB =
                    ABILITY_TYPES.register(
                            HIDE_IN_MOB_NAME,
                            key -> new AbilityType<>(
                                    key,
                                    StickyFingersHideInMobAbility
                                            ::new));

    public static final DeferredBlock<StickyFingersZipperBlock>
            STICKY_FINGERS_ZIPPER = BLOCKS.register(
                    "sticky_fingers_zipper",
                    () -> new StickyFingersZipperBlock(
                            Block.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                                    .strength(-1.0F, 3600000.0F)
                                    .sound(SoundType.CHAIN)
                                    .noOcclusion()
                                    // The open shape depends on the neighbours.
                                    .dynamicShape()
                                    // 1.16 cancelSuffocation
                                    .isSuffocating((state, level, pos) ->
                                            !state.getValue(
                                                    StickyFingersZipperBlock.OPEN))
                                    .isViewBlocking((state, level, pos) ->
                                            !state.getValue(
                                                    StickyFingersZipperBlock.OPEN))
                                    .noLootTable()
                                    .pushReaction(
                                            PushReaction.BLOCK)));

    public static final DeferredHolder<
            BlockEntityType<?>,
            BlockEntityType<StickyFingersZipperBlockEntity>>
            STICKY_FINGERS_ZIPPER_BLOCK_ENTITY =
                    BLOCK_ENTITY_TYPES.register(
                            "sticky_fingers_zipper",
                            () -> BlockEntityType.Builder.of(
                                            StickyFingersZipperBlockEntity::new,
                                            STICKY_FINGERS_ZIPPER.get())
                                    .build(null));

    public static final DeferredHolder<
            EntityType<?>, EntityType<StickyFingersStandEntity>>
            STICKY_FINGERS_ENTITY = ENTITY_TYPES.register(
                    "sticky_fingers",
                    key -> EntityType.Builder
                            .<StickyFingersStandEntity>of(
                                    StickyFingersStandEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.65F, 1.95F)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<
            EntityType<?>,
            EntityType<StickyFingersExtendedPunchEntity>>
            STICKY_FINGERS_EXTENDED_PUNCH_ENTITY = ENTITY_TYPES.register(
                    "stfn_extended_punch",
                    key -> EntityType.Builder
                            .<StickyFingersExtendedPunchEntity>of(
                                    StickyFingersExtendedPunchEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<
            EntityType<?>,
            EntityType<DetachedBodyPartEntity>>
            PLAYER_HEAD = ENTITY_TYPES.register(
                    "player_head",
                    key -> detachedPartType(key.toString()));
    public static final DeferredHolder<
            EntityType<?>,
            EntityType<DetachedBodyPartEntity>>
            PLAYER_ARM = ENTITY_TYPES.register(
                    "player_arm",
                    key -> detachedPartType(key.toString()));
    public static final DeferredHolder<
            EntityType<?>,
            EntityType<DetachedBodyPartEntity>>
            PLAYER_LEG = ENTITY_TYPES.register(
                    "player_leg",
                    key -> detachedPartType(key.toString()));

    public static final DeferredHolder<MobEffect, ZipWoundsEffect>
            ZIP_WOUNDS_EFFECT = MOB_EFFECTS.register(
                    "zip_wounds",
                    key -> new ZipWoundsEffect());
    public static final DeferredHolder<MobEffect, ZipperEffect>
            ZIPPER_EFFECT = MOB_EFFECTS.register(
                    "zipper",
                    key -> new ZipperEffect());

    public static final DeferredHolder<
            MenuType<?>,
            MenuType<EntityZipperStorageMenu>>
            STICKY_FINGERS_ENTITY_CONTAINER =
                    MENU_TYPES.register(
                            "stfn_entity_container",
                            key -> new MenuType<>(
                                    EntityZipperStorageMenu::new,
                                    FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<
            MenuType<?>,
            MenuType<BlockZipperStorageMenu>>
            STICKY_FINGERS_BLOCK_CONTAINER =
                    MENU_TYPES.register(
                            "stfn_block_container",
                            key -> new MenuType<>(
                                    BlockZipperStorageMenu::new,
                                    FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<SoundEvent, SoundEvent>
            BRUNO_STICKY_FINGERS = registerSound("bruno_summon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            BRUNO_BARRAGE = registerSound("bruno_barrage");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            BRUNO_HEAVY_PUNCH = registerSound("bruno_heavy_punch");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            BRUNO_PUNCH = registerSound("bruno_punch");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            BRUNO_ZIP = registerSound("bruno_zip");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            BRUNO_UNZIP = registerSound("bruno_unzip");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            STICKY_FINGERS_SUMMON =
                    registerSound("sticky_fingers_summon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            STICKY_FINGERS_UNSUMMON =
                    registerSound("sticky_fingers_unsummon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            STICKY_FINGERS_PUNCH =
                    registerSound("sticky_fingers_punch");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            STICKY_FINGERS_HEAVY_PUNCH =
                    registerSound("sticky_fingers_heavy_punch");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            STICKY_FINGERS_EXTENDED_PUNCH_SWING =
                    registerSound("sticky_fingers_extended_punch_swing");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            BRUNO_FINISHER = registerSound("bruno_finisher");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            STICKY_FINGERS_FINISHER =
                    registerSound("sticky_fingers_finisher");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            STICKY_FINGERS_UNZIP_PART =
                    registerSound("sticky_fingers_unzip_part");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            ZIPPER_CREATE = registerSound("zipper_create");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            ZIPPER_OPEN = registerSound("zipper_open");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            ZIPPER_CLOSE = registerSound("zipper_close");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            ZIPPER_REMOVE = registerSound("zipper_remove");

    public static final DeferredHolder<StandType, EntityStandType>
            STICKY_FINGERS =
                    STAND_TYPES.register(
                            "sticky_fingers",
                            StickyFingersAddon::createStand);

    public StickyFingersAddon(IEventBus modEventBus) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_VISUAL_CONTEXT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_SCOPED_PLAYER_MODEL_VISIBILITY_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_SCOPED_PLAYER_MODEL_POSE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_SCOPED_HUMANOID_ARMOR_VISIBILITY_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_CLIENT_ENTITY_CONTROLLER_PICK_SOURCE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_CLIENT_ENTITY_CONTROLLER_LOCAL_CAMERA_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_HUMANOID_MODEL_POST_SETUP_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_CARRIED_PASSENGER_ACTION_HOOKS_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_SHOUTS_V1)) {
            throw new IllegalStateException(
                    "RotP Sticky Fingers requires ROTP addon ABI 1 with "
                            + "namespaced ability resources and stand visual "
                            + "contexts plus scoped body/armor visibility, "
                            + "final humanoid pose, controller pick/camera, "
                            + "and carried-passenger action hooks.");
        }
        // the unzip follow-ups modify the running unzip punch (R770 core)
        if (!RotpAddonApi.supportsFeature(
                RotpAddonApi.FEATURE_STAND_ACTION_MODIFIERS_V1)) {
            throw new IllegalStateException(
                    "RotP Sticky Fingers requires the core Stand action "
                            + "modifiers (a core from the same release).");
        }
        // StickyFingersZipperAbility calls Ability.checkPerformerStun (R148 core)
        if (!RotpAddonApi.supportsFeature(
                RotpAddonApi.FEATURE_ABILITY_PERFORMER_STUN_V1)) {
            throw new IllegalStateException(
                    "RotP Sticky Fingers requires the core performer stun "
                            + "check (a core from the same release).");
        }

        CarriedPassengerActionHooks.register(
                id("detached_body_part"),
                (player, action) ->
                        DetachedBodyPartEntity.releaseCarriedBy(
                                player, true));
        StickyFingersCoreIntegrations.register();
        modEventBus.addListener(
                DetachedBodyPresentationPayload::register);
        modEventBus.addListener(
                DetachedBodyControlPayload::register);
        modEventBus.addListener(
                StickyFingersModEvents::registerAttributes);
        ABILITY_TYPES.register(modEventBus);
        STAND_EFFECT_TYPES.register(modEventBus);
        BLOCKS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        MOB_EFFECTS.register(modEventBus);
        MENU_TYPES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static boolean isStickyFingersActive(
            LivingEntity entity) {
        StandPower power = StandPower.get(entity);
        return power != null
                && power.getPowerType() == STICKY_FINGERS.get()
                && power.isSummoned();
    }

    private static DeferredHolder<
            AbilityType<?>,
            AbilityType<StickyFingersUnzipPartAbility>> unzipPart(
                    String name, DetachedPartType.Region part) {
        return ABILITY_TYPES.register(
                name,
                key -> new AbilityType<>(
                        key,
                        (type, id) -> new StickyFingersUnzipPartAbility(
                                type, id, part)));
    }

    private static DeferredHolder<
            EntityCustomEffectType<?>,
            EntityCustomEffectType<StickyFingersUnzipPartAbility.UnzipPartModifier>>
            unzipPartModifier(String name, DetachedPartType.Region part) {
        return STAND_EFFECT_TYPES.register(
                name,
                key -> new EntityCustomEffectType<>(
                        key,
                        type -> new StickyFingersUnzipPartAbility
                                .UnzipPartModifier(type, part)));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> registerSound(
            String path) {
        return SOUND_EVENTS.register(
                path, SoundEvent::createVariableRangeEvent);
    }

    private static EntityType<DetachedBodyPartEntity>
            detachedPartType(String id) {
        return EntityType.Builder
                .<DetachedBodyPartEntity>of(
                        DetachedBodyPartEntity::new,
                        MobCategory.MISC)
                .noSummon()
                .sized(0.6F, 0.6F)
                .clientTrackingRange(48)
                .updateInterval(2)
                .build(id);
    }

    private static EntityStandType createStand(ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addHumanoidStandStuff()
                .addAbility(
                        "punch",
                        STICKY_FINGERS_PUNCH_ABILITY,
                        punch -> punch
                                .punchImpactSound(STICKY_FINGERS_PUNCH))
                .addAbility(
                        "barrage",
                        ModStandAbilities.BARRAGE,
                        barrage -> barrage
                                .barrageHitSound(STICKY_FINGERS_PUNCH)
                                .barrageCrySound(BRUNO_BARRAGE))
                .addAbility(
                        "heavy_punch",
                        STICKY_FINGERS_HEAVY_PUNCH_ABILITY,
                        punch -> punch
                                .heavyPunchImpactSound(
                                        STICKY_FINGERS_HEAVY_PUNCH))
                .addAbility(
                        "heavy_charged",
                        ModStandAbilities.HEAVY_CHARGED)
                .addAbility(
                        UNZIP_PUNCH_NAME,
                        STICKY_FINGERS_UNZIP_PUNCH,
                        punch -> {
                            punch.heavyPunchImpactSound(
                                    STICKY_FINGERS_FINISHER);
                            punch.initIsFinisher("heavy_punch");
                        })
                .addAbility(
                        UNZIP_HEAD_NAME,
                        STICKY_FINGERS_UNZIP_HEAD,
                        ability -> ability.resolveLevelToUnlock(
                                StickyFingersPhase1Spec
                                        .UNZIP_PART_RESOLVE_LEVEL))
                .addAbility(
                        UNZIP_ARM_NAME,
                        STICKY_FINGERS_UNZIP_ARM,
                        ability -> ability.resolveLevelToUnlock(
                                StickyFingersPhase1Spec
                                        .UNZIP_PART_RESOLVE_LEVEL))
                .addAbility(
                        UNZIP_LEG_NAME,
                        STICKY_FINGERS_UNZIP_LEG,
                        ability -> ability.resolveLevelToUnlock(
                                StickyFingersPhase1Spec
                                        .UNZIP_PART_RESOLVE_LEVEL))
                .addAbility("grab", ModStandAbilities.GRAB)
                .addAbility("grab_throw", ModStandAbilities.GRAB_THROW)
                .addAbility(
                        "grab_punch",
                        STICKY_FINGERS_PUNCH_ABILITY,
                        punch -> {
                            punch.punchImpactSound(STICKY_FINGERS_PUNCH);
                            punch.initIsGrabVariation();
                        })
                .addAbility(
                        "grab_barrage",
                        ModStandAbilities.BARRAGE,
                        barrage -> {
                            barrage.barrageHitSound(STICKY_FINGERS_PUNCH)
                                    .barrageCrySound(BRUNO_BARRAGE);
                            barrage.initIsGrabVariation();
                        })
                .addAbility(
                        "grab_heavy_punch",
                        STICKY_FINGERS_HEAVY_PUNCH_ABILITY,
                        punch -> {
                            punch.heavyPunchImpactSound(
                                    STICKY_FINGERS_HEAVY_PUNCH);
                            punch.initIsGrabVariation();
                        })
                .addAbility("guard", ModStandAbilities.GUARD)
                .addAbility(
                        EXTENDED_PUNCH_NAME,
                        STICKY_FINGERS_EXTENDED_PUNCH,
                        ability -> ability.resolveLevelToUnlock(
                                StickyFingersPhase1Spec
                                        .EXTENDED_PUNCH_RESOLVE_LEVEL))
                .addAbility(
                        ZIPPER_NAME,
                        STICKY_FINGERS_ZIPPER_ABILITY,
                        ability -> ability.resolveLevelToUnlock(
                                StickyFingersPhase1Spec
                                        .ZIPPER_RESOLVE_LEVEL))
                .addAbility(
                        CLIMB_ZIPPER_NAME,
                        STICKY_FINGERS_CLIMB_ZIPPER)
                .addAbility(
                        HIDE_IN_MOB_NAME,
                        STICKY_FINGERS_HIDE_IN_MOB,
                        ability -> ability.resolveLevelToUnlock(
                                StickyFingersPhase1Spec
                                        .HIDE_IN_MOB_RESOLVE_LEVEL))
                .addAbility(
                        ZIP_USER_WOUNDS_NAME,
                        STICKY_FINGERS_ZIP_USER_WOUNDS)
                .addAbility(
                        ZIP_TARGET_WOUNDS_NAME,
                        STICKY_FINGERS_ZIP_TARGET_WOUNDS)
                .addAbility(
                        OPEN_STORAGE_NAME,
                        STICKY_FINGERS_OPEN_STORAGE,
                        ability -> ability.resolveLevelToUnlock(
                                StickyFingersPhase1Spec
                                        .OPEN_STORAGE_RESOLVE_LEVEL))
                .addAbility(
                        OPEN_INNER_STORAGE_NAME,
                        STICKY_FINGERS_OPEN_INNER_STORAGE,
                        ability -> ability.resolveLevelToUnlock(
                                StickyFingersPhase1Spec
                                        .OPEN_INNER_STORAGE_RESOLVE_LEVEL))
                .makeControlScheme("hotbar")
                    .bind("punch", InputMethod.CLICK, InputKey.LMB)
                    .bind("barrage", InputMethod.HOLD, InputKey.LMB)
                    .bind(
                            "heavy_punch",
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
                    .bind("guard", InputMethod.HOLD, InputKey.RMB)
                    .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
                    .addToHotbar(
                            EXTENDED_PUNCH_NAME,
                            0,
                            InputMethod.CLICK)
                    // 1.16 place_zipper was holdType: hold to keep zipping
                    .addToHotbar(ZIPPER_NAME, 0, InputMethod.HOLD)
                    .addToHotbar(
                            CLIMB_ZIPPER_NAME,
                            0,
                            InputMethod.HOLD)
                    .addToHotbar(
                            HIDE_IN_MOB_NAME,
                            0,
                            InputMethod.HOLD)
                    .addToHotbar(
                            ZIP_USER_WOUNDS_NAME,
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            ZIP_TARGET_WOUNDS_NAME,
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            OPEN_STORAGE_NAME,
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            OPEN_INNER_STORAGE_NAME,
                            0,
                            InputMethod.CLICK)
                .finalizeControlScheme()
                .makeControlScheme("keybinds")
                    .bind("punch", InputMethod.CLICK, InputKey.LMB)
                    .bind("barrage", InputMethod.HOLD, InputKey.LMB)
                    .bind(
                            "heavy_punch",
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
                    .bind("guard", InputMethod.HOLD, InputKey.RMB)
                .finalizeControlScheme()
                .addSkill(StandUnlockableSkill.startingAbility("punch"))
                .addSkill(StandUnlockableSkill.startingAbility("barrage"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "heavy_punch"))
                .addSkill(StandUnlockableSkill.startingAbility(
                                "heavy_charged")
                        .prerequisiteSkill("heavy_punch"))
                .addSkill(StandUnlockableSkill.startingAbility("grab"))
                .addSkill(StandUnlockableSkill.unlockableAbility(
                                "grab_throw", 100)
                        .withAbility(
                                "grab_punch",
                                "grab_barrage",
                                "grab_heavy_punch")
                        .prerequisiteSkill("grab"))
                .addSkill(StandUnlockableSkill.startingAbility("guard"))
                .addSkill(StandUnlockableSkill.unlockableAbility(
                        EXTENDED_PUNCH_NAME,
                        StickyFingersPhase1Spec
                                .EXTENDED_PUNCH_SKILL_POINTS))
                .addSkill(StandUnlockableSkill.unlockableAbility(
                        ZIPPER_NAME,
                        StickyFingersPhase1Spec
                                .ZIPPER_SKILL_POINTS))
                .addSkill(StandUnlockableSkill.startingAbility(
                        CLIMB_ZIPPER_NAME))
                .addSkill(StandUnlockableSkill.unlockableAbility(
                        HIDE_IN_MOB_NAME,
                        StickyFingersPhase1Spec
                                .HIDE_IN_MOB_SKILL_POINTS))
                .addSkill(StandUnlockableSkill.startingAbility(
                        ZIP_USER_WOUNDS_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        ZIP_TARGET_WOUNDS_NAME))
                .addSkill(StandUnlockableSkill.unlockableAbility(
                                OPEN_STORAGE_NAME,
                                StickyFingersPhase1Spec
                                        .OPEN_STORAGE_SKILL_POINTS)
                        .prerequisiteSkill(ZIPPER_NAME))
                .addSkill(StandUnlockableSkill.unlockableAbility(
                                OPEN_INNER_STORAGE_NAME,
                                StickyFingersPhase1Spec
                                        .OPEN_INNER_STORAGE_SKILL_POINTS)
                        .prerequisiteSkill(OPEN_STORAGE_NAME))
                .addHumanoidStandSkills();

        return new EntityStandType(
                new StandStats.Builder()
                        .power(12.0, 14.0)
                        .speed(11.0, 14.0)
                        .range(1.0, 2.0)
                        .durability(7.0, 7.0)
                        .precision(8.0, 10.0)
                        .randomWeight(2.0D)
                        .build(),
                moveset,
                STICKY_FINGERS_ENTITY.get(),
                id)
                .standControlType(StandControlType.CLOSE_RANGE_DIRECT)
                .manualControl(true)
                .distanceStrengthDecay(true)
                .standDimensions(0.65F, 1.95F)
                .summonShout(() -> BRUNO_STICKY_FINGERS)
                .summonSound(() -> STICKY_FINGERS_SUMMON)
                .unsummonSound(() -> STICKY_FINGERS_UNSUMMON);
    }
}
