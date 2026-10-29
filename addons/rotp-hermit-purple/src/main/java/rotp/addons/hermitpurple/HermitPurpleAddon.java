package rotp.addons.hermitpurple;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import rotp.core.api.RotpAddonApi;
import rotp.core.api.stand.StandDamageAuthorizers;
import rotp.core.api.stand.StandDamageQuery;
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
import rotp.core.util.functions.UtilFunctions;

import rotp.addons.hermitpurple.ability.HermitBarrierAbility;
import rotp.addons.hermitpurple.ability.HermitBreathAbility;
import rotp.addons.hermitpurple.ability.HermitDivinationAbility;
import rotp.addons.hermitpurple.ability.HermitGrappleAbility;
import rotp.addons.hermitpurple.ability.HermitHamonFollowupAbility;
import rotp.addons.hermitpurple.ability.HermitNoopAbility;
import rotp.addons.hermitpurple.ability.HermitSpiritCameraAbility;
import rotp.addons.hermitpurple.ability.HermitTargetSelectionAbility;
import rotp.addons.hermitpurple.ability.HermitThornsAbility;
import rotp.addons.hermitpurple.ability.HermitVineAttackAbility;
import rotp.addons.hermitpurple.entity.HermitBarrierVineEntity;
import rotp.addons.hermitpurple.entity.HermitGrappleVineEntity;
import rotp.addons.hermitpurple.entity.HermitPurpleStandEntity;
import rotp.addons.hermitpurple.entity.HermitVineProjectileEntity;
import rotp.addons.hermitpurple.network.HermitTargetPayloads;
import rotp.addons.hermitpurple.stand.HermitPurpleStandType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(HermitPurpleAddon.MOD_ID)
public final class HermitPurpleAddon {
    public static final String MOD_ID = "rotp_zhp";
    public static final ResourceLocation HAMON_TO_HERMIT_SOURCE =
            ResourceLocation.fromNamespaceAndPath(
                    MOD_ID, "hamon_to_hermit");

    public static final String HP_VINE_ATTACK_NAME = "hp_vine_attack";
    public static final String HP_VINE_SHIFT_NAME = "hp_vine_shift";
    public static final String HP_GRAB_NAME = "hp_grab";
    public static final String HP_GRAB_OD_NAME = "hp_grab_od";
    public static final String HP_GRAB_SC_NAME = "hp_grab_sc";
    public static final String HP_GRAB_HEAL_NAME = "hp_grab_heal";
    public static final String HP_BLOCK_NAME = "hp_block";
    public static final String HP_VINE_NAME = "hp_vine";
    public static final String HP_VINE_ENTITY_NAME = "hp_vine_entity";
    public static final String HP_BARRIER_NAME = "hp_barrier";
    public static final String HP_UNBARRIER_NAME = "hp_unbarrier";
    public static final String CRINGE_NAME = "cringe";
    public static final String HP_BREATH_NAME = "hp_breath";
    public static final String HP_DOXX_NAME = "hp_doxx";
    public static final String HP_CAMERA_NAME = "hp_camera";
    public static final String HP_TARGET_NAME = "hp_target";
    public static final String HP_BEAT_NAME = "hp_beat";

    public static final int ACTIVE_LASH_CAP =
            HermitPurpleSpec.VINE_ATTACK_ACTIVE_CAP;
    public static final TagKey<Item> CAMERA_ITEMS =
            TagKey.create(Registries.ITEM, id("camera"));

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

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<HermitVineAttackAbility>>
            HP_VINE_ATTACK = vineAttack(HP_VINE_ATTACK_NAME);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<HermitVineAttackAbility>>
            HP_VINE_SHIFT = vineAttack(HP_VINE_SHIFT_NAME);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<HermitGrappleAbility>>
            HP_GRAB = grapple(HP_GRAB_NAME);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<HermitHamonFollowupAbility>>
            HP_GRAB_OD = followup(HP_GRAB_OD_NAME);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<HermitHamonFollowupAbility>>
            HP_GRAB_SC = followup(HP_GRAB_SC_NAME);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<HermitHamonFollowupAbility>>
            HP_GRAB_HEAL = followup(HP_GRAB_HEAL_NAME);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<HermitThornsAbility>>
            HP_BLOCK = ABILITY_TYPES.register(
                    HP_BLOCK_NAME,
                    key -> new AbilityType<>(
                            key, HermitThornsAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<HermitGrappleAbility>>
            HP_VINE = grapple(HP_VINE_NAME);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<HermitGrappleAbility>>
            HP_VINE_ENTITY = grapple(HP_VINE_ENTITY_NAME);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<HermitBarrierAbility>>
            HP_BARRIER = barrier(HP_BARRIER_NAME);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<HermitBarrierAbility>>
            HP_UNBARRIER = barrier(HP_UNBARRIER_NAME);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<HermitNoopAbility>>
            CRINGE = ABILITY_TYPES.register(
                    CRINGE_NAME,
                    key -> new AbilityType<>(key, HermitNoopAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<HermitBreathAbility>>
            HP_BREATH = ABILITY_TYPES.register(
                    HP_BREATH_NAME,
                    key -> new AbilityType<>(key, HermitBreathAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<HermitDivinationAbility>>
            HP_DOXX = ABILITY_TYPES.register(
                    HP_DOXX_NAME,
                    key -> new AbilityType<>(
                            key, HermitDivinationAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<HermitSpiritCameraAbility>>
            HP_CAMERA = ABILITY_TYPES.register(
                    HP_CAMERA_NAME,
                    key -> new AbilityType<>(
                            key, HermitSpiritCameraAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<HermitTargetSelectionAbility>>
            HP_TARGET = ABILITY_TYPES.register(
                    HP_TARGET_NAME,
                    key -> new AbilityType<>(
                            key, HermitTargetSelectionAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<HermitHamonFollowupAbility>>
            HP_BEAT = followup(HP_BEAT_NAME);

    public static final DeferredHolder<
            EntityType<?>, EntityType<HermitPurpleStandEntity>>
            HERMIT_PURPLE_ENTITY = ENTITY_TYPES.register(
                    "hermito_purple",
                    key -> EntityType.Builder
                            .<HermitPurpleStandEntity>of(
                                    HermitPurpleStandEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.0F, 0.0F)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));
    public static final DeferredHolder<
            EntityType<?>, EntityType<HermitVineProjectileEntity>>
            HP_VINE_ENTITY_TYPE = ENTITY_TYPES.register(
                    "hp_vine",
                    key -> EntityType.Builder
                            .<HermitVineProjectileEntity>of(
                                    HermitVineProjectileEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(50)
                            .updateInterval(1)
                            .build(key.toString()));
    public static final DeferredHolder<
            EntityType<?>, EntityType<HermitGrappleVineEntity>>
            HP_GRAB_VINE_ENTITY = ENTITY_TYPES.register(
                    "hp_grab_vine",
                    key -> heldVineType(key.toString()));
    public static final DeferredHolder<
            EntityType<?>, EntityType<HermitGrappleVineEntity>>
            HP_GRAPPLING_VINE_ENTITY = ENTITY_TYPES.register(
                    "hp_grappling_vine",
                    key -> heldVineType(key.toString()));
    public static final DeferredHolder<
            EntityType<?>, EntityType<HermitBarrierVineEntity>>
            HP_BARRIER_VINE_ENTITY = ENTITY_TYPES.register(
                    "hp_barrier",
                    key -> EntityType.Builder
                            .<HermitBarrierVineEntity>of(
                                    HermitBarrierVineEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.2F, 0.2F)
                            .clientTrackingRange(64)
                            .updateInterval(1)
                            .build(key.toString()));

    public static final DeferredHolder<SoundEvent, SoundEvent>
            HP_SUMMON = sound("hp_summon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            HP_UNSUMMON = sound("hp_unsummon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            HP_THROW = sound("hp_throw");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            HP_GRAPPLE = sound("hp_grap");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            HP_GRAB_CATCH = sound("hp_grp_ent");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            HP_SPARK = sound("hp_spark");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            JOSEPH_HP = sound("joseph_hp");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            JOSEPH_OD = sound("joseph_od");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            JOSEPH_THROW = sound("joseph_throw");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            JOSEPH_OVERDRIVE = sound("joseph_over_drive");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            JOSEPH_BREATH = sound("joseph_breath");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            JOSEPH_CRINGE = sound("joseph_cringe");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            JONATHAN_THROW = sound("jonathan_throw");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            JONATHAN_ATTACK = sound("jonathan_at");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            JONATHAN_OVERDRIVE = sound("jonathan_over_drive");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            WEB_SLING = sound("web_sling");

    public static final DeferredHolder<
            StandType, HermitPurpleStandType>
            HERMIT_PURPLE = STAND_TYPES.register(
                    "hermito_purple",
                    HermitPurpleAddon::createStand);

    public HermitPurpleAddon(
            IEventBus modEventBus, ModContainer modContainer) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_VISUAL_CONTEXT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ADDON_PLAYER_ANIMATIONS_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_DAMAGE_AUTHORIZERS_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_AUTOMATED_STAND_GRANT_VETOES_V1)
                // attack-strength reset on perform (R146 core)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ENTITY_ACTION_HOOKS_V1)
                // guardsHitsOnUser opt-out (R147 core)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_USER_GUARD_V1)
                // 1.16 action shouts follow the sneak rule
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_SHOUTS_V1)) {
            throw new IllegalStateException(
                    "RotP Hermit Purple requires addon ABI 1 "
                            + "with namespaced ability resources "
                            + "Stand visual contexts, and addon "
                            + "player animations and Stand damage "
                            + "authorizers plus automated Stand "
                            + "grant vetoes, entity action hooks "
                            + "and the Stand user guard opt-out "
                            + "(a core from the same release).");
        }

        StandDamageAuthorizers.register(
                id("hermit_hamon"),
                HermitPurpleAddon::canHurtStandWithHamon);
        ABILITY_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        modEventBus.addListener(
                HermitPurpleGameEvents::registerAttributes);
        modEventBus.addListener(HermitTargetPayloads::register);
        modContainer.registerConfig(
                ModConfig.Type.COMMON, HermitPurpleConfig.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static boolean isHermitPurpleUser(LivingEntity entity) {
        StandPower power =
                entity != null ? StandPower.get(entity) : null;
        return power != null
                && power.getPowerType() == HERMIT_PURPLE.get();
    }

    private static boolean canHurtStandWithHamon(
            StandDamageQuery query) {
        return hasHermitHamonGates(query.attacker());
    }

    // 1.16 HermitPurpleStandType.tickUser integrated_stand gates
    public static boolean hasHermitHamonGates(LivingEntity attacker) {
        StandPower power =
                attacker != null ? StandPower.get(attacker) : null;
        return HermitPurpleConfig.HERMIT_HAMON.get()
                && power != null
                && power.getPowerType() == HERMIT_PURPLE.get()
                && power.getResolveLevel() > 3
                && controller(attacker) != null
                && UtilFunctions.itemHandFree(
                        attacker.getMainHandItem());
    }

    public static HermitPurpleStandEntity controller(
            LivingEntity entity) {
        StandPower power =
                entity != null ? StandPower.get(entity) : null;
        return power != null
                && power.getPowerType() == HERMIT_PURPLE.get()
                && power.isSummoned()
                && power.getSummonedStandEntity()
                        instanceof HermitPurpleStandEntity controller
                && controller.isAlive()
                && !controller.isRemoved()
                ? controller
                : null;
    }

    public static void discardActiveVines(LivingEntity entity) {
        HermitPurpleStandEntity controller = controller(entity);
        if (controller != null) {
            controller.discardActiveVines();
        }
    }

    private static DeferredHolder<
            AbilityType<?>, AbilityType<HermitVineAttackAbility>>
            vineAttack(String name) {
        return ABILITY_TYPES.register(
                name,
                key -> new AbilityType<>(
                        key, HermitVineAttackAbility::new));
    }

    private static DeferredHolder<
            AbilityType<?>, AbilityType<HermitGrappleAbility>>
            grapple(String name) {
        return ABILITY_TYPES.register(
                name,
                key -> new AbilityType<>(
                        key, HermitGrappleAbility::new));
    }

    private static DeferredHolder<
            AbilityType<?>, AbilityType<HermitHamonFollowupAbility>>
            followup(String name) {
        return ABILITY_TYPES.register(
                name,
                key -> new AbilityType<>(
                        key, HermitHamonFollowupAbility::new));
    }

    private static DeferredHolder<
            AbilityType<?>, AbilityType<HermitBarrierAbility>>
            barrier(String name) {
        return ABILITY_TYPES.register(
                name,
                key -> new AbilityType<>(
                        key, HermitBarrierAbility::new));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(
            String path) {
        return SOUND_EVENTS.register(
                path, SoundEvent::createVariableRangeEvent);
    }

    private static EntityType<HermitGrappleVineEntity>
            heldVineType(String id) {
        return EntityType.Builder
                .<HermitGrappleVineEntity>of(
                        HermitGrappleVineEntity::new,
                        MobCategory.MISC)
                .noSave()
                .noSummon()
                .sized(0.25F, 0.25F)
                .clientTrackingRange(50)
                .updateInterval(1)
                .build(id);
    }

    private static HermitPurpleStandType createStand(
            ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addAbility(HP_VINE_ATTACK_NAME, HP_VINE_ATTACK)
                .addAbility(HP_VINE_SHIFT_NAME, HP_VINE_SHIFT)
                .addAbility(
                        HP_GRAB_NAME,
                        HP_GRAB,
                        ability -> ability.resolveLevelToUnlock(2))
                .addAbility(HP_GRAB_OD_NAME, HP_GRAB_OD)
                .addAbility(HP_GRAB_SC_NAME, HP_GRAB_SC)
                .addAbility(HP_GRAB_HEAL_NAME, HP_GRAB_HEAL)
                .addAbility(
                        HP_BLOCK_NAME,
                        HP_BLOCK,
                        ability -> ability.resolveLevelToUnlock(3))
                .addAbility(
                        HP_VINE_NAME,
                        HP_VINE,
                        ability -> ability.resolveLevelToUnlock(1))
                .addAbility(
                        HP_VINE_ENTITY_NAME,
                        HP_VINE_ENTITY,
                        ability -> ability.resolveLevelToUnlock(1))
                .addAbility(HP_BARRIER_NAME, HP_BARRIER)
                .addAbility(HP_UNBARRIER_NAME, HP_UNBARRIER)
                .addAbility(
                        CRINGE_NAME,
                        CRINGE,
                        ability -> ability.resolveLevelToUnlock(4))
                .addAbility(HP_BREATH_NAME, HP_BREATH)
                .addAbility(HP_DOXX_NAME, HP_DOXX)
                .addAbility(HP_CAMERA_NAME, HP_CAMERA)
                .addAbility(HP_TARGET_NAME, HP_TARGET)
                .addAbility(HP_BEAT_NAME, HP_BEAT)
                .makeControlScheme("hotbar")
                    .bind(
                            HP_VINE_ATTACK_NAME,
                            InputMethod.CLICK,
                            InputKey.LMB)
                    // 1.16 shiftVariationOf(HP_VINE_ATTACK): Shift with the
                    // attack button. LMB is a direct bind, not a hotbar slot.
                    .bind(
                            HP_VINE_SHIFT_NAME,
                            InputMethod.CLICK,
                            InputKey.LMB.withModifier(
                                    InputKey.Modifier.SHIFT))
                    .bind(
                            HP_BLOCK_NAME,
                            InputMethod.HOLD,
                            InputKey.RMB)
                    .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
                    .addToHotbar(
                            HP_GRAB_NAME, 0, InputMethod.HOLD)
                    .addToHotbar(
                            CRINGE_NAME, 0, InputMethod.CLICK)
                    .addToHotbar(
                            HP_BREATH_NAME, 0, InputMethod.HOLD)
                    .addToHotbar(
                            HP_DOXX_NAME, 0, InputMethod.HOLD)
                    .addToHotbar(
                            HP_TARGET_NAME, 0, InputMethod.CLICK)
                    .addToHotbar(
                            HP_VINE_NAME, 0, InputMethod.HOLD)
                    .addToHotbar(
                            HP_BARRIER_NAME, 0, InputMethod.CLICK)
                    .addHotbarSlotVariation(
                            HP_VINE_ENTITY_NAME,
                            HP_VINE_NAME,
                            InputKey.Modifier.SHIFT,
                            InputMethod.HOLD)
                    .addHotbarSlotVariation(
                            HP_UNBARRIER_NAME,
                            HP_BARRIER_NAME,
                            InputKey.Modifier.SHIFT,
                            InputMethod.CLICK)
                .finalizeControlScheme()
                .makeControlScheme("keybinds")
                    .bind(
                            HP_VINE_ATTACK_NAME,
                            InputMethod.CLICK,
                            InputKey.LMB)
                    .bind(
                            HP_VINE_SHIFT_NAME,
                            InputMethod.CLICK,
                            InputKey.LMB.withModifier(
                                    InputKey.Modifier.SHIFT))
                    .bind(
                            HP_BLOCK_NAME,
                            InputMethod.HOLD,
                            InputKey.RMB)
                .finalizeControlScheme();

        for (String ability : new String[] {
                HP_VINE_ATTACK_NAME,
                HP_VINE_SHIFT_NAME,
                HP_GRAB_NAME,
                HP_GRAB_OD_NAME,
                HP_GRAB_SC_NAME,
                HP_GRAB_HEAL_NAME,
                HP_BLOCK_NAME,
                HP_VINE_NAME,
                HP_VINE_ENTITY_NAME,
                HP_BARRIER_NAME,
                HP_UNBARRIER_NAME,
                CRINGE_NAME,
                HP_BREATH_NAME,
                HP_DOXX_NAME,
                HP_CAMERA_NAME,
                HP_TARGET_NAME,
                HP_BEAT_NAME}) {
            moveset.addSkill(
                    StandUnlockableSkill.startingAbility(ability));
        }

        return new HermitPurpleStandType(
                new StandStats.Builder()
                        .power(HermitPurpleSpec.POWER)
                        .speed(HermitPurpleSpec.SPEED)
                        .range(
                                HermitPurpleSpec.EFFECTIVE_RANGE,
                                HermitPurpleSpec.MAX_RANGE)
                        .durability(
                                HermitPurpleSpec.DURABILITY)
                        .precision(
                                HermitPurpleSpec.PRECISION)
                        .randomWeight(2.0D)
                        .build(),
                moveset,
                HERMIT_PURPLE_ENTITY.get(),
                id)
                .standControlType(StandControlType.PHENOMENON)
                .manualControl(false)
                .distanceStrengthDecay(false)
                .standDimensions(0.0F, 0.0F)
                .summonShout(() -> JOSEPH_HP)
                .summonSound(() -> HP_SUMMON)
                .unsummonSound(() -> HP_UNSUMMON);
    }
}
