package rotp.addons.kraftwork;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import java.util.function.Supplier;

import rotp.core.api.RotpAddonApi;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.power.ModStandAbilities;
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
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import com.mojang.serialization.Codec;

import rotp.addons.kraftwork.ability.KraftWorkActionAbility;
import rotp.addons.kraftwork.ability.KraftWorkActionAbility.Mode;
import rotp.addons.kraftwork.ability.KraftWorkComboPunchAbility;
import rotp.addons.kraftwork.ability.KraftWorkGuardAbility;
import rotp.addons.kraftwork.ability.KraftWorkHeavyPunchAbility;
import rotp.addons.kraftwork.effect.LockedHandsEffect;
import rotp.addons.kraftwork.effect.LockedPositionEffect;
import rotp.addons.kraftwork.effect.TransportLockedEffect;
import rotp.addons.kraftwork.entity.KraftWorkBlockMarkerEntity;
import rotp.addons.kraftwork.entity.KraftWorkItemProjectile;
import rotp.addons.kraftwork.network.KraftWorkPayloads;
import rotp.addons.kraftwork.state.KraftWorkLockState;
import rotp.addons.kraftwork.state.KraftWorkUserState;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@Mod(KraftWorkAddon.MOD_ID)
public final class KraftWorkAddon {
    public static final String MOD_ID = "rotp_kraft_work";
    private static final int REQUIRED_CORE_ABI = 1;

    public static final String PUNCH = "kraft_work_punch";
    public static final String BARRAGE = "kraft_work_barrage";
    public static final String COMBO_PUNCH =
            "kraft_work_combo_punch";
    public static final String LOCK_ARMOR =
            "kraft_work_lock_armor";
    public static final String HEAVY_PUNCH =
            "kraft_work_heavy_punch";
    public static final String BLOCK = "kraft_work_block";
    // Moveset slot of the BLOCK ability. The core counts a guard in any slot,
    // as 1.16 did for every StandEntityBlock, but its auto-guard and click
    // timing, the mob combos (base_combos.json "block") and the starting
    // skill still look up the port-wide slot name "guard".
    public static final String GUARD = "guard";
    public static final String PLACE_PROJECTILE =
            "kraft_work_place_projectile";
    public static final String LOCK_SELF =
            "kraft_work_lock_yourself";
    public static final String LOCK_TARGET =
            "kraft_work_lock_target";
    public static final String RELEASE_TARGET =
            "kraft_work_release_target";
    public static final String ENERGY_ACCUMULATION =
            "kraft_work_energy_accumulation";
    public static final String RELEASE_PROJECTILE =
            "kraft_work_release_projectile";
    public static final String RELEASE_ALL =
            "kraft_work_release_all_projectiles";
    public static final String RELEASE_READY =
            "kraft_work_release_projectiles_r";
    public static final String RELEASE_NOT_READY =
            "kraft_work_release_projectiles_nr";
    public static final String RELEASE_ENDER_PEARL =
            "kraft_work_release_ender_pearl";
    public static final String RELEASE_BENEFICIAL =
            "kraft_work_release_beneficial";
    public static final String ADVANCED_RELEASE =
            "kraft_work_advanced_release";
    public static final String BLOCKING_ITEMS =
            "kraft_work_bi_status";

    public static final DeferredRegister<AbilityType<?>>
            ABILITY_TYPES = DeferredRegister.create(
                    JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<AttachmentType<?>>
            ATTACHMENT_TYPES = DeferredRegister.create(
                    NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID);
    public static final DeferredRegister<EntityType<?>>
            ENTITY_TYPES = DeferredRegister.create(
                    Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<MobEffect>
            MOB_EFFECTS = DeferredRegister.create(
                    Registries.MOB_EFFECT, MOD_ID);
    public static final DeferredRegister<SoundEvent>
            SOUND_EVENTS = DeferredRegister.create(
                    Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<StandType>
            STAND_TYPES = DeferredRegister.create(
                    JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<StandEntityPunchAbility>>
            PUNCH_TYPE = ABILITY_TYPES.register(
                    PUNCH,
                    key -> new AbilityType<>(
                            key, StandEntityPunchAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<StandEntityBarrageAbility>>
            BARRAGE_TYPE = ABILITY_TYPES.register(
                    BARRAGE,
                    key -> new AbilityType<>(
                            key, StandEntityBarrageAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<KraftWorkComboPunchAbility>>
            COMBO_PUNCH_TYPE = ABILITY_TYPES.register(
                    COMBO_PUNCH,
                    key -> new AbilityType<>(
                            key, KraftWorkComboPunchAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<KraftWorkHeavyPunchAbility>>
            HEAVY_PUNCH_TYPE = ABILITY_TYPES.register(
                    HEAVY_PUNCH,
                    key -> new AbilityType<>(
                            key, KraftWorkHeavyPunchAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<KraftWorkGuardAbility>>
            BLOCK_TYPE = ABILITY_TYPES.register(
                    BLOCK,
                     key -> new AbilityType<>(
                             key, KraftWorkGuardAbility::new));
    // 1.16 KraftWorkLockArmor: the heavy punch's opt-in follow-up.
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<
                    KraftWorkHeavyPunchAbility.LockArmorFollowup>>
            LOCK_ARMOR_TYPE = ABILITY_TYPES.register(
                    LOCK_ARMOR,
                    key -> new AbilityType<>(
                            key,
                            KraftWorkHeavyPunchAbility
                                    .LockArmorFollowup::new));

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<KraftWorkActionAbility>>
            PLACE_PROJECTILE_TYPE =
                    action(PLACE_PROJECTILE, Mode.PLACE_PROJECTILE);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<KraftWorkActionAbility>>
            LOCK_SELF_TYPE = action(LOCK_SELF, Mode.LOCK_SELF);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<KraftWorkActionAbility>>
            LOCK_TARGET_TYPE = action(LOCK_TARGET, Mode.LOCK_TARGET);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<KraftWorkActionAbility>>
            RELEASE_TARGET_TYPE =
                    action(RELEASE_TARGET, Mode.RELEASE_TARGET);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<KraftWorkActionAbility>>
            ENERGY_ACCUMULATION_TYPE =
                    action(
                            ENERGY_ACCUMULATION,
                            Mode.ENERGY_ACCUMULATION);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<KraftWorkActionAbility>>
            RELEASE_PROJECTILE_TYPE =
                    action(
                            RELEASE_PROJECTILE,
                            Mode.RELEASE_PROJECTILE);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<KraftWorkActionAbility>>
            RELEASE_ALL_TYPE =
                    action(RELEASE_ALL, Mode.RELEASE_ALL);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<KraftWorkActionAbility>>
            RELEASE_READY_TYPE =
                    action(RELEASE_READY, Mode.RELEASE_READY);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<KraftWorkActionAbility>>
            RELEASE_NOT_READY_TYPE =
                    action(
                            RELEASE_NOT_READY,
                            Mode.RELEASE_NOT_READY);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<KraftWorkActionAbility>>
            RELEASE_ENDER_PEARL_TYPE =
                    action(
                            RELEASE_ENDER_PEARL,
                            Mode.RELEASE_ENDER_PEARL);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<KraftWorkActionAbility>>
            RELEASE_BENEFICIAL_TYPE =
                    action(
                            RELEASE_BENEFICIAL,
                            Mode.RELEASE_BENEFICIAL);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<KraftWorkActionAbility>>
            ADVANCED_RELEASE_TYPE =
                    action(ADVANCED_RELEASE, Mode.ADVANCED_RELEASE);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<KraftWorkActionAbility>>
            BLOCKING_ITEMS_TYPE =
                    action(BLOCKING_ITEMS, Mode.BLOCKING_ITEMS);

    public static final Supplier<AttachmentType<KraftWorkLockState>>
            LOCK_STATE = ATTACHMENT_TYPES.register(
                    "lock_state",
                    () -> AttachmentType.serializable(
                                    holder -> holder instanceof Entity
                                            ? new KraftWorkLockState()
                                            : null)
                            .build());
    public static final Supplier<AttachmentType<KraftWorkUserState>>
            USER_STATE = ATTACHMENT_TYPES.register(
                    "user_state",
                    () -> AttachmentType.serializable(
                                    holder ->
                                            holder
                                                    instanceof LivingEntity
                                            ? new KraftWorkUserState()
                                            : null)
                            .build());
    // 1.16 KraftWorkStandType.setCanUpdateServerSide (:190-195) also told
    // the tracking clients (TrSetEntityCanUpdatePacket) to stop ticking a
    // held entity: the kinds the server holds without ticking carry it
    public static final Supplier<AttachmentType<Boolean>>
            HELD_STILL = ATTACHMENT_TYPES.register(
                    "held_still",
                    () -> AttachmentType.builder(() -> Boolean.FALSE)
                            .serialize(Codec.BOOL)
                            .sync(ByteBufCodecs.BOOL)
                            .build());

    public static final DeferredHolder<
            MobEffect, LockedPositionEffect>
            LOCKED_POSITION = MOB_EFFECTS.register(
                    "locked_position",
                    key -> new LockedPositionEffect());
    public static final DeferredHolder<
            MobEffect, LockedHandsEffect>
            LOCKED_MAIN_HAND = MOB_EFFECTS.register(
                    "locked_main_hand",
                    key -> new LockedHandsEffect());
    public static final DeferredHolder<
            MobEffect, LockedHandsEffect>
            LOCKED_OFF_HAND = MOB_EFFECTS.register(
                    "locked_off_hand",
                    key -> new LockedHandsEffect());
    public static final DeferredHolder<
            MobEffect, LockedPositionEffect>
            LOCKED_HELMET = MOB_EFFECTS.register(
                    "locked_helmet",
                    key -> new LockedPositionEffect());
    public static final DeferredHolder<
            MobEffect, LockedPositionEffect>
            LOCKED_CHESTPLATE = MOB_EFFECTS.register(
                    "locked_chestplate",
                    key -> new LockedPositionEffect());
    public static final DeferredHolder<
            MobEffect, LockedPositionEffect>
            LOCKED_LEGGINGS = MOB_EFFECTS.register(
                    "locked_leggings",
                    key -> new LockedPositionEffect());
    public static final DeferredHolder<
            MobEffect, LockedPositionEffect>
            TRANSPORTATION_LOCKED = MOB_EFFECTS.register(
                    "transportation_locked",
                    key -> new LockedPositionEffect());
    public static final DeferredHolder<
            MobEffect, TransportLockedEffect>
            FULL_TRANSPORTATION_LOCKED =
                    MOB_EFFECTS.register(
                            "full_transportation_locked",
                            key -> new TransportLockedEffect());

    public static final DeferredHolder<
            EntityType<?>, EntityType<StandEntity>>
            KRAFT_WORK_ENTITY = ENTITY_TYPES.register(
                    "kraft_work",
                    key -> EntityType.Builder.<StandEntity>of(
                                    StandEntity::new, MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(
                                    KraftWorkSpec.ENTITY_WIDTH,
                                    KraftWorkSpec.ENTITY_HEIGHT)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));
    public static final DeferredHolder<
            EntityType<?>, EntityType<KraftWorkItemProjectile>>
            KW_ITEM = ENTITY_TYPES.register(
                    "kw_item",
                    key -> EntityType.Builder
                            .<KraftWorkItemProjectile>of(
                                    KraftWorkItemProjectile::new,
                                    MobCategory.MISC)
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));
    public static final DeferredHolder<
            EntityType<?>, EntityType<KraftWorkBlockMarkerEntity>>
            KW_BLOCK = ENTITY_TYPES.register(
                    "kw_block",
                    key -> EntityType.Builder
                            .<KraftWorkBlockMarkerEntity>of(
                                    KraftWorkBlockMarkerEntity::new,
                                    MobCategory.MISC)
                            .noSummon()
                            .sized(0.98F, 0.98F)
                            .clientTrackingRange(16)
                            .updateInterval(10)
                            .build(key.toString()));

    public static final DeferredHolder<SoundEvent, SoundEvent>
            SALE_KRAFT_WORK = sound("sale_kraft_work");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            KRAFT_WORK_BLOCKS_A_PROJECTILE =
                    sound("kraft_work_blocks_a_projectile");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            KRAFT_WORK_GIVING_KINETIC_ENERGY =
                    sound("kraft_work_giving_kinetic_energy");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            KRAFT_WORK_RELEASED_PROJECTILE =
                    sound("kraft_work_released_projectile");

    public static final DeferredHolder<StandType, EntityStandType>
            KRAFT_WORK = STAND_TYPES.register(
                    "kraft_work", KraftWorkAddon::createStand);

    public KraftWorkAddon(IEventBus modEventBus) {
        requireCoreApi();
        ABILITY_TYPES.register(modEventBus);
        ATTACHMENT_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        MOB_EFFECTS.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        modEventBus.addListener(
                KraftWorkGameEvents::registerAttributes);
        modEventBus.addListener(KraftWorkPayloads::register);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static boolean isKraftWorkUser(
            LivingEntity entity) {
        StandPower power = StandPower.get(entity);
        return power != null
                && power.getPowerType() == KRAFT_WORK.get();
    }

    public static KraftWorkUserState userState(
            LivingEntity entity) {
        return entity.getData(USER_STATE);
    }

    private static DeferredHolder<
            AbilityType<?>, AbilityType<KraftWorkActionAbility>>
            action(String path, Mode mode) {
        return ABILITY_TYPES.register(
                path,
                key -> new AbilityType<>(
                        key,
                        (type, id) ->
                                new KraftWorkActionAbility(
                                        type, id, mode)));
    }

    private static DeferredHolder<SoundEvent, SoundEvent>
            sound(String path) {
        return SOUND_EVENTS.register(
                path, SoundEvent::createVariableRangeEvent);
    }

    private static EntityStandType createStand(
            ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addHumanoidStandStuff()
                .addAbility(PUNCH, ModStandAbilities.PUNCH)
                .addAbility(BARRAGE, ModStandAbilities.BARRAGE)
                .addAbility(
                        COMBO_PUNCH,
                        COMBO_PUNCH_TYPE,
                        ability -> {
                            ability.initIsFinisher(HEAVY_PUNCH);
                            ability.resolveLevelToUnlock(1);
                        })
                .addAbility(HEAVY_PUNCH, HEAVY_PUNCH_TYPE)
                .addAbility(
                        "heavy_charged",
                        ModStandAbilities.HEAVY_CHARGED)
                .addAbility("grab", ModStandAbilities.GRAB)
                .addAbility(
                        "grab_throw", ModStandAbilities.GRAB_THROW)
                .addAbility(
                        "grab_punch",
                        ModStandAbilities.PUNCH,
                        ability -> {
                            ability.initIsGrabVariation();
                        })
                .addAbility(
                        "grab_barrage",
                        ModStandAbilities.BARRAGE,
                        ability -> {
                            ability.initIsGrabVariation();
                        })
                .addAbility(
                        "grab_heavy_punch",
                        HEAVY_PUNCH_TYPE,
                        ability -> {
                            ability.initIsGrabVariation();
                        })
                .addAbility(LOCK_ARMOR, LOCK_ARMOR_TYPE)
                .addAbility(GUARD, BLOCK_TYPE)
                .addAbility(PLACE_PROJECTILE, PLACE_PROJECTILE_TYPE)
                .addAbility(LOCK_SELF, LOCK_SELF_TYPE)
                .addAbility(LOCK_TARGET, LOCK_TARGET_TYPE)
                .addAbility(RELEASE_TARGET, RELEASE_TARGET_TYPE)
                .addAbility(
                        ENERGY_ACCUMULATION,
                        ENERGY_ACCUMULATION_TYPE)
                .addAbility(
                        RELEASE_PROJECTILE,
                        RELEASE_PROJECTILE_TYPE)
                .addAbility(RELEASE_ALL, RELEASE_ALL_TYPE)
                .addAbility(RELEASE_READY, RELEASE_READY_TYPE)
                .addAbility(
                        RELEASE_NOT_READY,
                        RELEASE_NOT_READY_TYPE)
                .addAbility(
                        RELEASE_ENDER_PEARL,
                        RELEASE_ENDER_PEARL_TYPE)
                .addAbility(
                        RELEASE_BENEFICIAL,
                        RELEASE_BENEFICIAL_TYPE)
                .addAbility(
                        ADVANCED_RELEASE,
                        ADVANCED_RELEASE_TYPE)
                .addAbility(
                        BLOCKING_ITEMS,
                        BLOCKING_ITEMS_TYPE)
                .makeControlScheme("hotbar")
                    .bind(PUNCH, InputMethod.CLICK, InputKey.LMB)
                    .bind(BARRAGE, InputMethod.HOLD, InputKey.LMB)
                    .bind(
                            HEAVY_PUNCH,
                            InputMethod.CLICK,
                            InputKey.RMB)
                    .bind(
                            "heavy_charged",
                            InputMethod.HOLD,
                            MovesetBuilder
                                    .DEFAULT_CHARGED_HEAVY_INPUT)
                    .bind(
                            "grab",
                            InputMethod.CLICK,
                            MovesetBuilder.DEFAULT_GRAB_INPUT)
                    .bind(
                            "grab_throw",
                            InputMethod.HOLD,
                            MovesetBuilder.DEFAULT_GRAB_THROW_INPUT)
                    .bind(GUARD, InputMethod.HOLD, InputKey.RMB)
                    .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
                    .addToHotbar(
                            PLACE_PROJECTILE,
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            LOCK_SELF, 0, InputMethod.CLICK)
                    .addToHotbar(
                            LOCK_TARGET, 0, InputMethod.HOLD)
                    // 1.16's Shift variation replaced the whole slot; the
                    // core keeps Shift on it (no fall back to the Lock
                    // Target hold), so the click fires on the press.
                    .addHotbarSlotVariation(
                            RELEASE_TARGET,
                            LOCK_TARGET,
                            InputKey.Modifier.SHIFT,
                            InputMethod.CLICK)
                    .addToHotbar(
                            ENERGY_ACCUMULATION,
                            0,
                            InputMethod.HOLD)
                    .addToHotbar(
                            RELEASE_PROJECTILE,
                            0,
                            InputMethod.CLICK)
                    .addHotbarSlotVariation(
                            ADVANCED_RELEASE,
                            RELEASE_PROJECTILE,
                            InputKey.Modifier.SHIFT,
                            InputMethod.CLICK)
                    .addToHotbar(
                            BLOCKING_ITEMS,
                            0,
                            InputMethod.CLICK)
                .finalizeControlScheme()
                .makeControlScheme("keybinds")
                    .bind(PUNCH, InputMethod.CLICK, InputKey.LMB)
                    .bind(BARRAGE, InputMethod.HOLD, InputKey.LMB)
                    .bind(
                            HEAVY_PUNCH,
                            InputMethod.CLICK,
                            InputKey.RMB)
                    .bind(
                            "heavy_charged",
                            InputMethod.HOLD,
                            MovesetBuilder
                                    .DEFAULT_CHARGED_HEAVY_INPUT)
                    .bind(
                            "grab",
                            InputMethod.CLICK,
                            MovesetBuilder.DEFAULT_GRAB_INPUT)
                    .bind(
                            "grab_throw",
                            InputMethod.HOLD,
                            MovesetBuilder.DEFAULT_GRAB_THROW_INPUT)
                    .bind(GUARD, InputMethod.HOLD, InputKey.RMB)
                .finalizeControlScheme()
                .addSkill(
                        StandUnlockableSkill.startingAbility(PUNCH))
                .addSkill(
                        StandUnlockableSkill.startingAbility(BARRAGE))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                        HEAVY_PUNCH)
                                .withAbility(
                                        COMBO_PUNCH,
                                        LOCK_ARMOR))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                        "heavy_charged")
                                .prerequisiteSkill(HEAVY_PUNCH))
                .addSkill(
                        StandUnlockableSkill.startingAbility(GUARD))
                .addSkill(
                        StandUnlockableSkill.startingAbility("grab"))
                .addSkill(
                        StandUnlockableSkill.unlockableAbility(
                                        "grab_throw", 100)
                                .withAbility(
                                        "grab_punch",
                                        "grab_barrage",
                                        "grab_heavy_punch")
                                .prerequisiteSkill("grab"))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                PLACE_PROJECTILE))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                LOCK_SELF))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                LOCK_TARGET))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                RELEASE_TARGET))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                ENERGY_ACCUMULATION))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                RELEASE_PROJECTILE))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                RELEASE_ALL))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                RELEASE_READY))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                RELEASE_NOT_READY))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                RELEASE_ENDER_PEARL))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                RELEASE_BENEFICIAL))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                ADVANCED_RELEASE))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                BLOCKING_ITEMS))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                "manual_control"));

        return new EntityStandType(
                new StandStats.Builder()
                        .power(KraftWorkSpec.POWER)
                        .speed(
                                KraftWorkSpec.SPEED,
                                KraftWorkSpec.MAX_SPEED)
                        .range(
                                KraftWorkSpec.EFFECTIVE_RANGE,
                                KraftWorkSpec.MAX_RANGE)
                        .durability(
                                KraftWorkSpec.DURABILITY)
                        .precision(
                                KraftWorkSpec.PRECISION,
                                KraftWorkSpec.MAX_PRECISION)
                        .randomWeight(2.0D)
                        .build(),
                moveset,
                KRAFT_WORK_ENTITY.get(),
                id)
                .standControlType(StandControlType.CLOSE_RANGE_DIRECT)
                .manualControl(true)
                .distanceStrengthDecay(true)
                .standDimensions(
                        KraftWorkSpec.ENTITY_WIDTH,
                        KraftWorkSpec.ENTITY_HEIGHT)
                .summonShout(() -> SALE_KRAFT_WORK)
                .summonSound(() -> ModSoundEvents.STAND_SUMMON)
                .unsummonSound(() -> ModSoundEvents.STAND_UNSUMMON);
    }

    private static void requireCoreApi() {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_VISUAL_CONTEXT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ACTION_SUMMON_LOCK_HOOK_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ACTION_STAND_STUN_HOOK_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_POWER_ACTION_SLOT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_ACTION_PORT_SLOT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_PLAYER_ACTION_GATE_V1)) {
            throw new IllegalStateException(
                    "RotP Kraft Work requires ROTP addon ABI 1 "
                            + "with namespaced ability resources, "
                            + "Stand visual contexts, the action "
                            + "summon lock hook, the action Stand "
                            + "stun hook, the Stand-power action slot, "
                            + "the Stand action port slot and the "
                            + "player action gate.");
        }
    }
}
