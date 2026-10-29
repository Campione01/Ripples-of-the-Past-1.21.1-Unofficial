package rotp.addons.ovatheworld;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import rotp.core.api.RotpAddonApi;
import rotp.core.api.timestop.TimeStopAudioContext;
import rotp.core.api.timestop.TimeStopAudioCue;
import rotp.core.api.timestop.TimeStopAudioDecision;
import rotp.core.api.timestop.TimeStopBehaviorPolicies;
import rotp.core.api.timestop.TimeStopBehaviorPolicy;
import rotp.core.api.timestop.TimeStopStartupCostContext;
import rotp.core.api.timestop.TimeStopStartupCostDecision;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.timestop.TimeStopLearning;
import rotp.core.util.sound.OstSoundList;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.impl.stands._entitybase.StandEntityBlockAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.impl.stands.theworld.TheWorldTSPunchAbility;
import rotp.core.impl.stands.theworld.TimeResumeAbility;
import rotp.core.impl.stands.theworld.TimeStopBlinkAbility;

import rotp.addons.ovatheworld.ability.OvaHeavyPunchAbility;
import rotp.addons.ovatheworld.ability.OvaSmashAbility;
import rotp.addons.ovatheworld.ability.OvaTimeStopAbility;
import rotp.addons.ovatheworld.ability.OvaUltraPunchAbility;
import rotp.addons.ovatheworld.entity.OvaTheWorldStandEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(OvaTheWorldAddon.MOD_ID)
public final class OvaTheWorldAddon {
    public static final String MOD_ID = "rotp_ova_addon";
    private static final int REQUIRED_CORE_ABI = 1;

    public static final String PUNCH = "punch";
    public static final String BARRAGE = "barrage";
    public static final String HEAVY_PUNCH = "heavy_punch";
    public static final String HEAVY_CHARGED = "heavy_charged";
    public static final String SMASH = "smash";
    public static final String ULTRA_PUNCH = "ultra_punch";
    public static final String TS_PUNCH = "ts_punch";
    public static final String GUARD = "guard";
    public static final String GRAB = "grab";
    public static final String GRAB_THROW = "grab_throw";
    public static final String TIME_STOP = "time_stop";
    public static final String TIME_STOP_BLINK = "time_stop_blink";

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
            AbilityType<?>, AbilityType<StandEntityPunchAbility>>
            THE_WORLD_OVA_PUNCH = ABILITY_TYPES.register(
                    "the_world_ova_punch",
                    key -> new AbilityType<>(
                            key, StandEntityPunchAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<StandEntityBarrageAbility>>
            THE_WORLD_OVA_BARRAGE = ABILITY_TYPES.register(
                    "the_world_ova_barrage",
                    key -> new AbilityType<>(
                            key, StandEntityBarrageAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<StandEntityBarrageAbility>>
            THE_WORLD_OVA_KICK_BARRAGE = ABILITY_TYPES.register(
                    "the_world_ova_kick_barrage",
                    key -> new AbilityType<>(
                            key, StandEntityBarrageAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<OvaSmashAbility>>
            THE_WORLD_OVA_SMASH = ABILITY_TYPES.register(
                    "the_world_ova_smash",
                    key -> new AbilityType<>(
                            key, OvaSmashAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<OvaHeavyPunchAbility>>
            THE_WORLD_OVA_HEAVY_PUNCH = ABILITY_TYPES.register(
                    "the_world_ova_heavy_punch",
                    key -> new AbilityType<>(
                            key, OvaHeavyPunchAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<OvaUltraPunchAbility>>
            THE_WORLD_OVA_ULTRA_PUNCH = ABILITY_TYPES.register(
                    "the_world_ova_ultra_punch",
                    key -> new AbilityType<>(
                            key, OvaUltraPunchAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<TheWorldTSPunchAbility>>
            THE_WORLD_OVA_TS_PUNCH = ABILITY_TYPES.register(
                    "the_world_ova_ts_punch",
                    key -> new AbilityType<>(
                            key, TheWorldTSPunchAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<StandEntityBlockAbility>>
            THE_WORLD_OVA_BLOCK = ABILITY_TYPES.register(
                    "the_world_ova_block",
                    key -> new AbilityType<>(
                            key, StandEntityBlockAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<OvaTimeStopAbility>>
            THE_WORLD_OVA_TIME_STOP = ABILITY_TYPES.register(
                    "the_world_ova_time_stop",
                    key -> new AbilityType<>(
                            key, OvaTimeStopAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<TimeResumeAbility>>
            THE_WORLD_OVA_TIME_RESUME = ABILITY_TYPES.register(
                    "the_world_ova_time_resume",
                    key -> new AbilityType<>(
                            key, TimeResumeAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<TimeStopBlinkAbility>>
            THE_WORLD_OVA_TIME_STOP_BLINK = ABILITY_TYPES.register(
                    "the_world_ova_ts_blink",
                    key -> new AbilityType<>(
                            key, TimeStopBlinkAbility::new));

    public static final DeferredHolder<
            EntityType<?>, EntityType<OvaTheWorldStandEntity>>
            THE_WORLD_OVA_ENTITY = ENTITY_TYPES.register(
                    "the_world_ova",
                    key -> EntityType.Builder
                            .<OvaTheWorldStandEntity>of(
                                    OvaTheWorldStandEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.85F, 2.35F)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<SoundEvent, SoundEvent>
            THE_WORLD_OVA_SUMMON =
                    sound("the_world_ova_summon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            THE_WORLD_OVA_UNSUMMON =
                    sound("the_world_ova_unsummon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            THE_WORLD_OVA_HEAVY_PUNCH_SOUND =
                    sound("the_world_ova_heavy_punch");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            THE_WORLD_OVA_BARRAGE_SOUND =
                    sound("the_world_ova_barrage");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            THE_WORLD_OVA_ULTRA_PUNCH_SOUND =
                    sound("the_world_ova_ultra_punch");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            THE_WORLD_OVA_TIME_STOP_SOUND =
                    sound("the_world_ova_time_stop");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            THE_WORLD_OVA_TIME_STOP_BLINK_SOUND =
                    sound("the_world_ova_ts_blink");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            THE_WORLD_OVA_BARRAGE_HIT =
                    sound("the_world_ova_barrage_hit");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            THE_WORLD_OVA_TIME_RESUME_SOUND =
                    sound("the_world_ova_time_resume");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIO_OVA_THE_WORLD = sound("dio_ova_the_world");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIO_OVA_TIME_STOP = sound("dio_ova_time_stop");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIO_OVA_CANT_MOVE = sound("dio_ova_cant_move");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIO_OVA_TIME_RESUMES = sound("dio_ova_time_resume");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIO_OVA_APPEAR = sound("dio_ova_appear");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIO_THE_WORLD_OVA = sound("dio_the_world_ova");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIO_OVA_DIE = sound("dio_ova_die");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIO_OVA_BARRAGE = sound("dio_ova_barrage");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            DIO_OVA_TIMES_UP = sound("dio_ova_times_up");

    // The locked source registers this family but supplies no OST audio.
    public static final OstSoundList THE_WORLD_OVA_OST =
            new OstSoundList(id("the_world_ova_ost"), SOUND_EVENTS);

    // 1.16 THE_WORLD_OVA_TIME_STOP staminaCost(250) against the core's 225,
    // scaled like the core cost for a released charge.
    private static final TimeStopBehaviorPolicy TIME_STOP_COST =
            new TimeStopBehaviorPolicy() {
                @Override
                public TimeStopStartupCostDecision startupCost(
                        TimeStopStartupCostContext context) {
                    return TimeStopStartupCostDecision.override(
                            context.defaultCost()
                                    * OvaTimeStopAbility
                                            .LEGACY_INITIAL_STAMINA
                                    / TimeStopLearning.BASE_STAMINA_COST);
                }

                // 1.16 addTimeResumeVoiceLine(DIO_OVA_TIME_RESUMES, true)
                // and (DIO_OVA_TIMES_UP, false): the core's resume voice
                // cue, 30 ticks before the end of a stop of 100+ ticks.
                @Override
                public TimeStopAudioDecision audio(
                        TimeStopAudioContext context) {
                    return context.cue() == TimeStopAudioCue.RESUME_VOICE
                            ? TimeStopAudioDecision.sound(
                                    context.isManualResume()
                                            ? DIO_OVA_TIME_RESUMES
                                            : DIO_OVA_TIMES_UP)
                            : TimeStopAudioDecision.pass();
                }
            };

    public static final DeferredHolder<StandType, EntityStandType>
            THE_WORLD_OVA = STAND_TYPES.register(
                    "the_world_ova", OvaTheWorldAddon::createStand);

    public OvaTheWorldAddon(IEventBus modEventBus) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_VISUAL_CONTEXT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_TIME_STOP_LIFECYCLE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_RANDOM_WEIGHTS_V1)
                // blink base costs, Resolve 4 charge (R146 core)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_TIME_STOP_ABILITY_TUNING_V1)
                // 1.16 action shouts follow the sneak rule
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_SHOUTS_V1)) {
            throw new IllegalStateException(
                    "OVA The World requires ROTP addon ABI 1 "
                            + "with namespaced abilities, Stand "
                            + "visual contexts, time-stop lifecycle, "
                            + "random weights and time-stop ability "
                            + "tuning (a core from the same release).");
        }

        ABILITY_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        TimeStopBehaviorPolicies.register(
                id("time_stop_cost"), id("the_world_ova"), TIME_STOP_COST);
        modEventBus.addListener(
                OvaTheWorldModEvents::registerAttributes);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(
            String path) {
        return SOUND_EVENTS.register(
                path, SoundEvent::createVariableRangeEvent);
    }

    private static EntityStandType createStand(ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addHumanoidStandStuff()
                .addAbility(
                        PUNCH,
                        THE_WORLD_OVA_PUNCH,
                        punch -> punch.punchImpactSound(
                                ModSoundEvents.STAND_PUNCH_LIGHT))
                .addAbility(
                        BARRAGE,
                        THE_WORLD_OVA_BARRAGE,
                        // 1.16 standSound(THE_WORLD_OVA_BARRAGE) also
                        // played while the Stand was arms-only.
                        barrage -> barrage
                                .barrageCrySound(
                                        THE_WORLD_OVA_BARRAGE_SOUND)
                                .barrageHitSound(
                                        THE_WORLD_OVA_BARRAGE_HIT)
                                .barrageCryInArmsOnly())
                .addAbility(
                        HEAVY_PUNCH,
                        THE_WORLD_OVA_HEAVY_PUNCH,
                        // 1.16 standSound(DIO_OVA_DIE) at PERFORM start.
                        // The heavy punch and the smash are plain
                        // StandEntityHeavyAttack builders: one perform tick,
                        // and StandEntityPunch's separate hit and tail pulses.
                        // The heavy punch takes its recovery when recovery
                        // starts (getStandRecoveryTicks).
                        punch -> {
                            punch.setDefaultPhaseLength(ActionPhase.PERFORM, 1);
                            punch.detachedPunchKnockback();
                            punch.recoveryPhaseEntryTiming();
                            punch.heavyPunchImpactSound(
                                            THE_WORLD_OVA_HEAVY_PUNCH_SOUND)
                                    .heavyPunchPerformSound(DIO_OVA_DIE);
                        })
                .addAbility(
                        "heavy_charged",
                        ModStandAbilities.HEAVY_CHARGED)
                .addAbility(
                        SMASH,
                        THE_WORLD_OVA_SMASH,
                        smash -> {
                            smash.heavyPunchImpactSound(
                                    THE_WORLD_OVA_HEAVY_PUNCH_SOUND);
                            smash.initIsFinisher(HEAVY_PUNCH);
                            smash.setDefaultPhaseLength(ActionPhase.PERFORM, 1);
                            smash.detachedPunchKnockback();
                            smash.recoveryPhaseEntryTiming();
                        })
                .addAbility(
                        ULTRA_PUNCH,
                        THE_WORLD_OVA_ULTRA_PUNCH,
                        // 1.16 TheWorldOvaUltraPunch is built from a plain
                        // StandEntityHeavyAttack.Builder as well.
                        ultra -> {
                            ultra.setDefaultPhaseLength(ActionPhase.PERFORM, 1);
                            ultra.detachedPunchKnockback();
                            ultra.recoveryPhaseEntryTiming();
                        })
                .addAbility(TS_PUNCH, THE_WORLD_OVA_TS_PUNCH)
                .addAbility("grab", ModStandAbilities.GRAB)
                .addAbility(
                        "grab_throw",
                        ModStandAbilities.GRAB_THROW)
                .addAbility(
                        "grab_punch",
                        THE_WORLD_OVA_PUNCH,
                        punch -> {
                            punch.punchImpactSound(
                                    ModSoundEvents
                                            .STAND_PUNCH_LIGHT);
                            punch.initIsGrabVariation();
                        })
                .addAbility(
                        "grab_barrage",
                        THE_WORLD_OVA_BARRAGE,
                        barrage -> {
                            barrage.barrageCrySound(
                                    THE_WORLD_OVA_BARRAGE_SOUND);
                            barrage.barrageHitSound(
                                    THE_WORLD_OVA_BARRAGE_HIT);
                            barrage.barrageCryInArmsOnly();
                            barrage.initIsGrabVariation();
                        })
                .addAbility(
                        "grab_heavy_punch",
                        THE_WORLD_OVA_HEAVY_PUNCH,
                        punch -> {
                            punch.heavyPunchImpactSound(
                                    THE_WORLD_OVA_HEAVY_PUNCH_SOUND);
                            punch.heavyPunchPerformSound(DIO_OVA_DIE);
                            punch.initIsGrabVariation();
                        })
                .addAbility(GUARD, THE_WORLD_OVA_BLOCK)
                .addAbility(
                        TIME_STOP,
                        THE_WORLD_OVA_TIME_STOP,
                        ability ->
                                ability.setIgnoresPerformerStun())
                // 1.16 TheWorldTimeStopInstant: behind the target, at 0.8
                // of the OVA time stop's 250 / 7.5.
                .addAbility(
                        TIME_STOP_BLINK,
                        THE_WORLD_OVA_TIME_STOP_BLINK,
                        ability -> ability
                                .setTeleportBehindEntity()
                                .setBaseTimeStopStaminaCosts(
                                        OvaTimeStopAbility
                                                .LEGACY_INITIAL_STAMINA,
                                        OvaTimeStopAbility
                                                .LEGACY_STAMINA_PER_TICK))
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
                            MovesetBuilder
                                    .DEFAULT_GRAB_THROW_INPUT)
                    .bind(GUARD, InputMethod.HOLD, InputKey.RMB)
                    .makeHotbar(
                            0, USE_SPECIAL, SWITCH_SPECIAL)
                    .addToHotbar(
                            ULTRA_PUNCH, 0, InputMethod.HOLD)
                    // The 1.16 blink (the_world_ova_time_resume's SHIFT
                    // variation) is its own slot after the time stop.
                    .addToHotbar(
                            TIME_STOP, 0, InputMethod.HOLD)
                    .addToHotbar(
                            TIME_STOP_BLINK, 0, InputMethod.CLICK)
                    .addToHotbar(
                            TS_PUNCH, 0, InputMethod.CLICK)
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
                            MovesetBuilder
                                    .DEFAULT_GRAB_THROW_INPUT)
                    .bind(GUARD, InputMethod.HOLD, InputKey.RMB)
                .finalizeControlScheme()
                .addSkill(
                        StandUnlockableSkill.startingAbility(PUNCH))
                .addSkill(
                        StandUnlockableSkill.startingAbility(BARRAGE))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                HEAVY_PUNCH))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                        "heavy_charged")
                                .prerequisiteSkill(HEAVY_PUNCH))
                .addSkill(
                        StandUnlockableSkill.startingAbility(SMASH))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                ULTRA_PUNCH))
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
                        StandUnlockableSkill.unlockableAbility(
                                TIME_STOP, 5000))
                .addSkill(
                        StandUnlockableSkill.unlockableAbility(
                                        TS_PUNCH, 6000)
                                .prerequisiteSkill(TIME_STOP))
                .addHumanoidStandSkills();

        return new EntityStandType(
                new StandStats.Builder()
                        .power(17.0D)
                        .speed(16.0D)
                        .range(2.5D, 10.0D)
                        .durability(16.5D)
                        .precision(16.0D)
                        .randomWeight(2.0D)
                        .build(),
                moveset,
                THE_WORLD_OVA_ENTITY.get(),
                id)
                .standControlType(StandControlType.CLOSE_RANGE_DIRECT)
                .manualControl(true)
                .distanceStrengthDecay(true)
                .standDimensions(0.85F, 2.35F)
                .summonShout(() -> DIO_OVA_THE_WORLD)
                .summonSound(() -> THE_WORLD_OVA_SUMMON)
                .unsummonSound(() -> THE_WORLD_OVA_UNSUMMON);
    }
}
