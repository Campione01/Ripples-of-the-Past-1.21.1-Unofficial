package rotp.addons.madeinheaven;

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
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.util.sound.OstSoundList;

import rotp.addons.madeinheaven.ability.LightSpeedDashAbility;
import rotp.addons.madeinheaven.ability.MadeInHeavenBarrageAbility;
import rotp.addons.madeinheaven.ability.MadeInHeavenChopAbility;
import rotp.addons.madeinheaven.ability.MadeInHeavenHeavyPunchAbility;
import rotp.addons.madeinheaven.ability.TimeAuraAbility;
import rotp.addons.madeinheaven.ability.TimeSystemAbility;
import rotp.addons.madeinheaven.ability.UniverseResetAbility;
import rotp.addons.madeinheaven.entity.MadeInHeavenStandEntity;
import rotp.addons.madeinheaven.network.TimeAccelerationStatePayload;
import rotp.addons.madeinheaven.state.TimeAccelerationState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@Mod(MadeInHeavenAddon.MOD_ID)
public final class MadeInHeavenAddon {
    public static final String MOD_ID = "rotp_mih";
    public static final int REQUIRED_CORE_ABI = 1;

    public static final float STAND_WIDTH = 0.7F;
    public static final float STAND_HEIGHT = 1.8F;
    public static final double POWER = 13.0D;
    public static final double SPEED = 20.0D;
    public static final double EFFECTIVE_RANGE = 8.0D;
    public static final double MAX_RANGE = 10.0D;
    public static final double DURABILITY = 14.0D;
    public static final double PRECISION = 10.0D;

    public static final String CHOP_NAME = "chop";
    public static final String DASH_NAME = "light_speed_dash";
    public static final String TIME_AURA_NAME = "time_aura";
    public static final String TIME_SYSTEM_NAME = "time_system";
    public static final String TIME_SYSTEM_CLEAR_NAME =
            "time_system_clear";
    public static final String UNIVERSE_RESET_NAME =
            "universe_reset";

    public static final DeferredRegister<AbilityType<?>>
            ABILITY_TYPES = DeferredRegister.create(
                    JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<EntityType<?>>
            ENTITY_TYPES = DeferredRegister.create(
                    Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<SoundEvent>
            SOUND_EVENTS = DeferredRegister.create(
                    Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<StandType>
            STAND_TYPES = DeferredRegister.create(
                    JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);
    public static final DeferredRegister<AttachmentType<?>>
            ATTACHMENT_TYPES = DeferredRegister.create(
                    NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID);

    public static final Supplier<AttachmentType<TimeAccelerationState>>
            TIME_ACCELERATION_STATE = ATTACHMENT_TYPES.register(
                    "time_acceleration_state",
                    () -> AttachmentType.builder(
                                    TimeAccelerationState::new)
                            .build());

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<MadeInHeavenChopAbility>>
            CHOP = ABILITY_TYPES.register(
                    CHOP_NAME,
                    key -> new AbilityType<>(
                            key, MadeInHeavenChopAbility::new));
    // 1.16 mih_barrage / mih_heavy_punch shouted from the user
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<MadeInHeavenBarrageAbility>>
            BARRAGE = ABILITY_TYPES.register(
                    "mih_barrage",
                    key -> new AbilityType<>(
                            key, MadeInHeavenBarrageAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<MadeInHeavenHeavyPunchAbility>>
            HEAVY_PUNCH = ABILITY_TYPES.register(
                    "mih_heavy_punch",
                    key -> new AbilityType<>(
                            key, MadeInHeavenHeavyPunchAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<LightSpeedDashAbility>>
            LIGHT_SPEED_DASH = ABILITY_TYPES.register(
                    DASH_NAME,
                    key -> new AbilityType<>(
                            key, LightSpeedDashAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<TimeAuraAbility>>
            TIME_AURA = ABILITY_TYPES.register(
                    TIME_AURA_NAME,
                    key -> new AbilityType<>(
                            key, TimeAuraAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<TimeSystemAbility>>
            TIME_SYSTEM = ABILITY_TYPES.register(
                    TIME_SYSTEM_NAME,
                    key -> new AbilityType<>(
                            key,
                            (type, abilityId) ->
                                    new TimeSystemAbility(
                                            type,
                                            abilityId,
                                            false)));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<TimeSystemAbility>>
            TIME_SYSTEM_CLEAR = ABILITY_TYPES.register(
                    TIME_SYSTEM_CLEAR_NAME,
                    key -> new AbilityType<>(
                            key,
                            (type, abilityId) ->
                                    new TimeSystemAbility(
                                            type,
                                            abilityId,
                                            true)));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<UniverseResetAbility>>
            UNIVERSE_RESET = ABILITY_TYPES.register(
                    UNIVERSE_RESET_NAME,
                    key -> new AbilityType<>(
                            key, UniverseResetAbility::new));

    public static final DeferredHolder<
            EntityType<?>, EntityType<MadeInHeavenStandEntity>>
            MADE_IN_HEAVEN_ENTITY = ENTITY_TYPES.register(
                    "made_in_heaven",
                    key -> EntityType.Builder
                            .<MadeInHeavenStandEntity>of(
                                    MadeInHeavenStandEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(STAND_WIDTH, STAND_HEIGHT)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<SoundEvent, SoundEvent>
            MIH_SUMMON = sound("mih_summon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            MIH_UNSUMMON = sound("mih_unsummon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            MIH_BARRAGE = sound("mih_barrage");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            MIH_CHOP = sound("mih_chop");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            MIH_DASH = sound("mih_dash");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            MIH_THROAT_SLICE = sound("mih_throat_slice");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            MIH_TIME_ACCELERATION =
                    sound("mih_time_acceleration");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            MIH_SUMMON_USER = sound("mih_summon_user");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            MIH_BARRAGE_USER = sound("mih_barrage_user");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            MIH_HEAVY_PUNCH_USER =
                    sound("mih_heavy_punch_user");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            MIH_CHOP_USER = sound("mih_chop_user");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            MIH_DASH_USER = sound("mih_dash_user");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            MIH_TIME_ACCELERATION_USER =
                    sound("mih_time_acceleration_user");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            MIH_UNIVERSE_RESET_USER =
                    sound("mih_universe_reset_user");
    public static final OstSoundList MIH_OST =
            new OstSoundList(id("mih_ost"), SOUND_EVENTS);

    public static final DeferredHolder<StandType, EntityStandType>
            MADE_IN_HEAVEN = STAND_TYPES.register(
                    "made_in_heaven",
                    MadeInHeavenAddon::createStand);

    public MadeInHeavenAddon(
            IEventBus modEventBus, ModContainer modContainer) {
        requireCoreApi();
        ABILITY_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        ATTACHMENT_TYPES.register(modEventBus);
        modContainer.registerConfig(
                ModConfig.Type.CLIENT,
                MadeInHeavenClientConfig.SPEC);
        modEventBus.addListener(
                MadeInHeavenGameEvents::registerAttributes);
        modEventBus.addListener(
                MadeInHeavenAddon::registerPayloads);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static TimeAccelerationState accelerationState(
            Level level) {
        return level.getData(TIME_ACCELERATION_STATE);
    }

    public static boolean isMadeInHeavenUser(
            net.minecraft.world.entity.LivingEntity entity) {
        StandPower power = StandPower.get(entity);
        return power != null
                && power.getPowerType() == MADE_IN_HEAVEN.get();
    }

    private static void registerPayloads(
            RegisterPayloadHandlersEvent event) {
        // "2": the snapshot no longer carries tick-rate fields.
        event.registrar("2").playToClient(
                TimeAccelerationStatePayload.TYPE,
                TimeAccelerationStatePayload.STREAM_CODEC,
                TimeAccelerationStatePayload::handle);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(
            String path) {
        return SOUND_EVENTS.register(
                path, SoundEvent::createVariableRangeEvent);
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
                                .FEATURE_STAND_POWER_TRANSITIONS_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_TIME_STOP_LIFECYCLE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ABILITY_SHOUTS_V1)) {
            throw new IllegalStateException(
                    "RotP Made in Heaven requires ROTP addon ABI 1 "
                            + "with namespaced ability resources, "
                            + "Stand visual contexts, Stand power "
                            + "transitions, and time-stop lifecycle "
                            + "events.");
        }
    }

    private static EntityStandType createStand(ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addHumanoidStandStuff()
                .addAbility(
                        "punch",
                        ModStandAbilities.PUNCH,
                        punch -> punch
                                .detachedLightCounterKnockback()
                                .punchImpactSound(
                                        ModSoundEvents
                                                .STAND_PUNCH_LIGHT)
                                .cooldown(3))
                .addAbility(
                        "barrage",
                        BARRAGE,
                        // 1.16 standSound(MIH_BARRAGE) was the Stand cry, also
                        // with the arms only
                        barrage -> barrage
                                .barrageCrySound(MIH_BARRAGE)
                                .barrageCryInArmsOnly())
                .addAbility(
                        "heavy_punch",
                        HEAVY_PUNCH,
                        punch -> {
                            punch.detachedPunchKnockback()
                                    .heavyPunchImpactSound(
                                            ModSoundEvents
                                                    .STAND_PUNCH_HEAVY);
                            punch.setDefaultPhaseLength(
                                    ActionPhase.PERFORM, 1);
                        })
                .addAbility(
                        CHOP_NAME,
                        CHOP,
                        // 1.16 Chop: the default heavy impact, and MIH_CHOP
                        // once when the task is set
                        chop -> {
                            chop.detachedPunchKnockback()
                                    .heavyPunchCrySound(MIH_CHOP)
                                    .initIsFinisher("heavy_punch");
                            chop.setDefaultPhaseLength(
                                    ActionPhase.PERFORM, 1);
                        })
                .addAbility(
                        "heavy_charged",
                        ModStandAbilities.HEAVY_CHARGED)
                .addAbility("grab", ModStandAbilities.GRAB)
                .addAbility(
                        "grab_throw",
                        ModStandAbilities.GRAB_THROW)
                .addAbility(
                        "grab_punch",
                        ModStandAbilities.PUNCH,
                        punch -> {
                            punch.punchImpactSound(
                                    ModSoundEvents
                                            .STAND_PUNCH_LIGHT);
                            punch.initIsGrabVariation();
                        })
                .addAbility(
                        "grab_barrage",
                        BARRAGE,
                        barrage -> {
                            barrage.barrageCrySound(MIH_BARRAGE);
                            barrage.initIsGrabVariation();
                        })
                .addAbility(
                        "grab_heavy_punch",
                        HEAVY_PUNCH,
                        punch -> {
                            punch.heavyPunchImpactSound(
                                    ModSoundEvents
                                            .STAND_PUNCH_HEAVY);
                            punch.initIsGrabVariation();
                        })
                .addAbility("guard", ModStandAbilities.GUARD)
                .addAbility(DASH_NAME, LIGHT_SPEED_DASH)
                .addAbility(TIME_AURA_NAME, TIME_AURA)
                .addAbility(TIME_SYSTEM_NAME, TIME_SYSTEM)
                .addAbility(
                        TIME_SYSTEM_CLEAR_NAME,
                        TIME_SYSTEM_CLEAR)
                .addAbility(
                        UNIVERSE_RESET_NAME,
                        UNIVERSE_RESET)
                .makeControlScheme("hotbar")
                    .bind(
                            "punch",
                            InputMethod.CLICK,
                            InputKey.LMB)
                    .bind(
                            "barrage",
                            InputMethod.HOLD,
                            InputKey.LMB)
                    .bind(
                            "heavy_punch",
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
                            MovesetBuilder
                                    .DEFAULT_GRAB_THROW_INPUT)
                    .bind(
                            "guard",
                            InputMethod.HOLD,
                            InputKey.RMB)
                    .makeHotbar(
                            0, USE_SPECIAL, SWITCH_SPECIAL)
                    // 1.16 mih_dash was holdType(5)
                    .addToHotbar(
                            DASH_NAME,
                            0,
                            InputMethod.HOLD)
                    .addToHotbar(
                            TIME_AURA_NAME,
                            0,
                            InputMethod.HOLD)
                    .addToHotbar(
                            TIME_SYSTEM_NAME,
                            0,
                            InputMethod.HOLD)
                .finalizeControlScheme()
                .makeControlScheme("keybinds")
                    .bind(
                            "punch",
                            InputMethod.CLICK,
                            InputKey.LMB)
                    .bind(
                            "barrage",
                            InputMethod.HOLD,
                            InputKey.LMB)
                    .bind(
                            "heavy_punch",
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
                            MovesetBuilder
                                    .DEFAULT_GRAB_THROW_INPUT)
                    .bind(
                            "guard",
                            InputMethod.HOLD,
                            InputKey.RMB)
                .finalizeControlScheme()
                .addSkill(
                        StandUnlockableSkill
                                .startingAbility("punch"))
                .addSkill(
                        StandUnlockableSkill
                                .startingAbility("barrage"))
                .addSkill(
                        StandUnlockableSkill
                                .startingAbility("heavy_punch")
                                .withAbility(CHOP_NAME))
                .addSkill(
                        StandUnlockableSkill
                                .startingAbility(
                                        "heavy_charged")
                                .prerequisiteSkill(
                                        "heavy_punch"))
                .addSkill(
                        StandUnlockableSkill
                                .startingAbility("guard"))
                .addSkill(
                        StandUnlockableSkill
                                .startingAbility("grab"))
                .addSkill(
                        StandUnlockableSkill
                                .unlockableAbility(
                                        "grab_throw", 100)
                                .withAbility(
                                        "grab_punch",
                                        "grab_barrage",
                                        "grab_heavy_punch")
                                .prerequisiteSkill("grab"))
                .addSkill(
                        StandUnlockableSkill
                                .unlockableAbility(
                                        DASH_NAME, 100))
                .addSkill(
                        StandUnlockableSkill
                                .startingAbility(
                                        TIME_AURA_NAME))
                .addSkill(
                        StandUnlockableSkill
                                .startingAbility(
                                        TIME_SYSTEM_NAME)
                                .withAbility(
                                        TIME_SYSTEM_CLEAR_NAME))
                .addSkill(
                        StandUnlockableSkill
                                .unlockableAbility(
                                        UNIVERSE_RESET_NAME,
                                        5000)
                                .prerequisiteSkill(
                                        TIME_SYSTEM_NAME))
                .addHumanoidStandSkills();

        return new EntityStandType(
                new StandStats.Builder()
                        .power(POWER)
                        .speed(SPEED)
                        .range(EFFECTIVE_RANGE, MAX_RANGE)
                        .durability(DURABILITY)
                        .precision(PRECISION)
                        .randomWeight(0.1D)
                        .build(),
                moveset,
                MADE_IN_HEAVEN_ENTITY.get(),
                id)
                .standControlType(StandControlType.CLOSE_RANGE_DIRECT)
                .manualControl(true)
                .distanceStrengthDecay(true)
                .standDimensions(STAND_WIDTH, STAND_HEIGHT)
                .summonShout(() -> MIH_SUMMON_USER)
                .summonSound(() -> MIH_SUMMON)
                .unsummonSound(() -> MIH_UNSUMMON)
                .ost(() -> MIH_OST);
    }
}
