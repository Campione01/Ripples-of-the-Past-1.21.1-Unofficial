package rotp.addons.cmoon;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import rotp.core.api.RotpAddonApi;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.impl.stands.theworld.TimeStopAbility;

import rotp.addons.cmoon.ability.CMoonBarrageAbility;
import rotp.addons.cmoon.ability.CMoonHeavyPunchAbility;
import rotp.addons.cmoon.ability.CMoonPunchAbility;
import rotp.addons.cmoon.ability.CenterOfGravityAbility;
import rotp.addons.cmoon.ability.DestroyAbility;
import rotp.addons.cmoon.ability.GravityCounterAbility;
import rotp.addons.cmoon.ability.GravityShiftAbility;
import rotp.addons.cmoon.ability.HeartInversionAbility;
import rotp.addons.cmoon.effect.InversionEffect;
import rotp.addons.cmoon.effect.ShiftEffect;
import rotp.addons.cmoon.entity.CMoonStandEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(CMoonAddon.MOD_ID)
public final class CMoonAddon {
    public static final String MOD_ID = "rotp_cm";
    public static final int REQUIRED_CORE_ABI = 1;

    public static final float STAND_WIDTH = 0.7F;
    public static final float STAND_HEIGHT = 2.0F;
    public static final double POWER = 0.0D;
    public static final double SPEED = 11.0D;
    public static final double EFFECTIVE_RANGE = 20.0D;
    public static final double MAX_RANGE = 20.0D;
    public static final double DURABILITY = 8.0D;
    public static final double PRECISION = 6.0D;
    public static final double LEGACY_RANDOM_WEIGHT = 0.5D;
    public static final int STAND_COLOR = 9222001;
    public static final int LEGACY_TIME_STOP_MAX_TICKS = 0;

    public static final String PUNCH = "punch";
    public static final String BARRAGE = "barrage";
    public static final String HEAVY_PUNCH = "heavy_punch";
    public static final String HEART_INVERSION = "heart_inversion";
    public static final String GUARD = "guard";
    public static final String GRAVITY_COUNTER = "gravity_counter";
    public static final String CENTER_OF_GRAVITY =
            "center_of_gravity";
    public static final String GRAVITY_SHIFT = "gravity_shift";
    public static final String DESTROY = "destroy";
    public static final String DESTROY_BARRAGE_COMPAT =
            "cm_destroy_barrage";
    public static final String HEAVY_CHARGED = "heavy_charged";
    public static final String BUY_MY_PATREON = "buy_my_patreon";

    public static final DeferredRegister<AbilityType<?>>
            ABILITY_TYPES = DeferredRegister.create(
                    JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<EntityType<?>>
            ENTITY_TYPES = DeferredRegister.create(
                    Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<MobEffect>
            MOB_EFFECTS = DeferredRegister.create(
                    Registries.MOB_EFFECT, MOD_ID);
    public static final DeferredRegister<ParticleType<?>>
            PARTICLE_TYPES = DeferredRegister.create(
                    Registries.PARTICLE_TYPE, MOD_ID);
    public static final DeferredRegister<SoundEvent>
            SOUND_EVENTS = DeferredRegister.create(
                    Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<StandType>
            STAND_TYPES = DeferredRegister.create(
                    JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<CMoonPunchAbility>>
            C_MOON_PUNCH = ABILITY_TYPES.register(
                    PUNCH,
                    key -> new AbilityType<>(
                            key, CMoonPunchAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<CMoonBarrageAbility>>
            C_MOON_BARRAGE = ABILITY_TYPES.register(
                    BARRAGE,
                    key -> new AbilityType<>(
                            key, CMoonBarrageAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<CMoonHeavyPunchAbility>>
            C_MOON_HEAVY_PUNCH = ABILITY_TYPES.register(
                    HEAVY_PUNCH,
                    key -> new AbilityType<>(
                            key, CMoonHeavyPunchAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<HeartInversionAbility>>
            C_MOON_HEART_INVERSION = ABILITY_TYPES.register(
                    HEART_INVERSION,
                    key -> new AbilityType<>(
                            key, HeartInversionAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<GravityCounterAbility>>
            C_MOON_GRAVITY_COUNTER = ABILITY_TYPES.register(
                    GRAVITY_COUNTER,
                    key -> new AbilityType<>(
                            key, GravityCounterAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<CenterOfGravityAbility>>
            C_MOON_CENTER_OF_GRAVITY = ABILITY_TYPES.register(
                    CENTER_OF_GRAVITY,
                    key -> new AbilityType<>(
                            key, CenterOfGravityAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<GravityShiftAbility>>
            C_MOON_GRAVITY_SHIFT = ABILITY_TYPES.register(
                    GRAVITY_SHIFT,
                    key -> new AbilityType<>(
                            key, GravityShiftAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<DestroyAbility>>
            C_MOON_DESTROY = ABILITY_TYPES.register(
                    DESTROY,
                    key -> new AbilityType<>(
                            key, DestroyAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<DestroyAbility>>
            C_MOON_DESTROY_BARRAGE = ABILITY_TYPES.register(
                    DESTROY_BARRAGE_COMPAT,
                    key -> new AbilityType<>(
                            key, DestroyAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<TimeStopAbility>>
            C_MOON_BUY_MY_PATREON = ABILITY_TYPES.register(
                    BUY_MY_PATREON,
                    key -> new AbilityType<>(
                            key, TimeStopAbility::new));

    public static final DeferredHolder<
            EntityType<?>, EntityType<CMoonStandEntity>>
            C_MOON_ENTITY = ENTITY_TYPES.register(
                    "cmoon",
                    key -> EntityType.Builder
                            .<CMoonStandEntity>of(
                                    CMoonStandEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(STAND_WIDTH, STAND_HEIGHT)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<MobEffect, InversionEffect>
            CM_INVERSION = MOB_EFFECTS.register(
                    "cm_inversion",
                    key -> new InversionEffect());
    public static final DeferredHolder<MobEffect, ShiftEffect>
            CM_SHIFT = MOB_EFFECTS.register(
                    "cm_shift",
                    key -> new ShiftEffect());
    public static final DeferredHolder<
            ParticleType<?>, SimpleParticleType>
            HAMON_SPARK_PURPLE = PARTICLE_TYPES.register(
                    "hamon_spark_purple",
                    () -> new SimpleParticleType(false));
    public static final DeferredHolder<
            ParticleType<?>, SimpleParticleType>
            HAMON_AURA_PURPLE = PARTICLE_TYPES.register(
                    "hamon_aura_purple",
                    () -> new SimpleParticleType(false));

    public static final DeferredHolder<SoundEvent, SoundEvent>
            C_MOON_SUMMON_SOUND = sound("cm_summon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            C_MOON_UNSUMMON_SOUND = sound("cm_unsummon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            C_MOON_PUNCH_SOUND = sound("cm_punch");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            C_MOON_HEAVY_PUNCH_SOUND =
                    sound("cm_heavy_punch");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            C_MOON_BARRAGE_SOUND = sound("cm_barrage");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            C_MOON_HEART_INVERSION_SOUND =
                    sound("cm_heart_inversion");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            C_MOON_GRAVITY_SHIFT_SOUND =
                    sound("cm_gravity_shift");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            C_MOON_GRAVITY_COUNTER_SOUND =
                    sound("cm_gravity_counter");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            C_MOON_CENTER_OF_GRAVITY_SOUND =
                    sound("cm_center_of_gravity");

    public static final DeferredHolder<StandType, EntityStandType>
            C_MOON = STAND_TYPES.register(
                    "cmoon", CMoonAddon::createStand);

    public CMoonAddon(
            IEventBus modEventBus, ModContainer modContainer) {
        requireCoreApi();
        ABILITY_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        MOB_EFFECTS.register(modEventBus);
        PARTICLE_TYPES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        modEventBus.addListener(CMoonModEvents::registerAttributes);
        modContainer.registerConfig(
                ModConfig.Type.COMMON, CMoonConfig.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
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
                                .FEATURE_STAND_VISUAL_CONTEXT_V1)) {
            throw new IllegalStateException(
                    "RotP C-Moon requires ROTP addon ABI 1 with "
                            + "namespaced ability resources and "
                            + "Stand visual contexts.");
        }
    }

    private static EntityStandType createStand(ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addHumanoidStandStuff()
                .addAbility(
                        PUNCH,
                        C_MOON_PUNCH,
                        punch -> punch.punchImpactSound(
                                C_MOON_PUNCH_SOUND))
                .addAbility(
                        BARRAGE,
                        C_MOON_BARRAGE,
                        // 1.16 standSound(CM_BARRAGE) also cried with the
                        // arms only.
                        barrage -> barrage
                                .barrageHitSound(
                                        ModSoundEvents.STAND_PUNCH_LIGHT)
                                .barrageCrySound(
                                        C_MOON_BARRAGE_SOUND)
                                .barrageCryInArmsOnly())
                .addAbility(
                        HEAVY_PUNCH,
                        C_MOON_HEAVY_PUNCH,
                        heavy -> heavy.heavyPunchImpactSound(
                                ModSoundEvents.STAND_PUNCH_HEAVY))
                .addAbility(
                        HEART_INVERSION,
                        C_MOON_HEART_INVERSION,
                        heart -> {
                            heart.initIsFinisher(HEAVY_PUNCH);
                            heart.heavyPunchImpactSound(
                                    C_MOON_HEART_INVERSION_SOUND);
                            heart.heavyPunchPerformSound(
                                    C_MOON_HEART_INVERSION_SOUND);
                        })
                .addAbility(GUARD, ModStandAbilities.GUARD)
                .addAbility(
                        "heavy_charged",
                        ModStandAbilities.HEAVY_CHARGED)
                .addAbility(
                        GRAVITY_COUNTER,
                        C_MOON_GRAVITY_COUNTER)
                .addAbility(
                        CENTER_OF_GRAVITY,
                        C_MOON_CENTER_OF_GRAVITY)
                .addAbility(
                        GRAVITY_SHIFT,
                        C_MOON_GRAVITY_SHIFT)
                .addAbility(DESTROY, C_MOON_DESTROY)
                .makeControlScheme("hotbar")
                    .bind(PUNCH, InputMethod.CLICK, InputKey.LMB)
                    .bind(BARRAGE, InputMethod.HOLD, InputKey.LMB)
                    .bind(
                            HEAVY_PUNCH,
                            InputMethod.CLICK,
                            InputKey.RMB)
                    .bind(GUARD, InputMethod.HOLD, InputKey.RMB)
                    .bind(
                            "heavy_charged",
                            InputMethod.HOLD,
                            MovesetBuilder
                                    .DEFAULT_CHARGED_HEAVY_INPUT)
                    .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
                    .addToHotbar(
                            GRAVITY_COUNTER,
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            CENTER_OF_GRAVITY,
                            0,
                            InputMethod.HOLD)
                    .addToHotbar(
                            DESTROY, 0, InputMethod.HOLD)
                    .addToHotbar(
                            GRAVITY_SHIFT,
                            0,
                            InputMethod.HOLD)
                .finalizeControlScheme()
                .makeControlScheme("keybinds")
                    .bind(PUNCH, InputMethod.CLICK, InputKey.LMB)
                    .bind(BARRAGE, InputMethod.HOLD, InputKey.LMB)
                    .bind(
                            HEAVY_PUNCH,
                            InputMethod.CLICK,
                            InputKey.RMB)
                    .bind(GUARD, InputMethod.HOLD, InputKey.RMB)
                    .bind(
                            "heavy_charged",
                            InputMethod.HOLD,
                            MovesetBuilder
                                    .DEFAULT_CHARGED_HEAVY_INPUT)
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
                                HEART_INVERSION))
                .addSkill(
                        StandUnlockableSkill.startingAbility(GUARD))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                "heavy_charged")
                                .prerequisiteSkill(HEAVY_PUNCH))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                GRAVITY_COUNTER))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                CENTER_OF_GRAVITY))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                GRAVITY_SHIFT))
                .addSkill(
                        StandUnlockableSkill.startingAbility(DESTROY))
                .addHumanoidStandSkills();

        return new EntityStandType(
                new StandStats.Builder()
                        .power(POWER)
                        .speed(SPEED)
                        .range(EFFECTIVE_RANGE, MAX_RANGE)
                        .durability(DURABILITY)
                        .precision(PRECISION)
                        .randomWeight(0.5D)
                        .build(),
                moveset,
                C_MOON_ENTITY.get(),
                id)
                .standControlType(StandControlType.LONG_DISTANCE_OPERATION)
                .manualControl(true)
                .distanceStrengthDecay(false)
                .standDimensions(STAND_WIDTH, STAND_HEIGHT)
                .summonSound(() -> C_MOON_SUMMON_SOUND)
                .unsummonSound(() -> C_MOON_UNSUMMON_SOUND);
    }
}
