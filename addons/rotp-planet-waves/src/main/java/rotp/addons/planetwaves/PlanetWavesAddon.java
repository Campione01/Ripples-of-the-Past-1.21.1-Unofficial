package rotp.addons.planetwaves;

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
import rotp.core.util.sound.OstSoundList;

import rotp.addons.planetwaves.ability.MeteorAttractionAbility;
import rotp.addons.planetwaves.ability.MeteorAttractionAbility.Trajectory;
import rotp.addons.planetwaves.ability.PlanetWavesGrabAbility;
import rotp.addons.planetwaves.effect.TrueBlindnessEffect;
import rotp.addons.planetwaves.entity.MeteoriteEntity;
import rotp.addons.planetwaves.entity.PlanetWavesStandEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(PlanetWavesAddon.MOD_ID)
public final class PlanetWavesAddon {
    public static final String MOD_ID = "rotp_pw";
    public static final int REQUIRED_CORE_ABI = 1;

    public static final float STAND_WIDTH = 0.7F;
    public static final float STAND_HEIGHT = 2.1F;
    public static final double POWER = 17.0D;
    public static final double SPEED = 14.0D;
    public static final double EFFECTIVE_RANGE = 2.0D;
    public static final double MAX_RANGE = 10.0D;
    public static final double DURABILITY = 18.0D;
    public static final double PRECISION = 7.0D;

    private static final String PUNCH = "punch";
    private static final String BARRAGE = "barrage";
    private static final String HEAVY_PUNCH = "heavy_punch";
    private static final String GUARD = "guard";
    private static final String VERTICAL = "vertical";
    private static final String VERTICAL_SHOWER = "vertical_shower";
    private static final String HORIZONTAL = "horizontal";
    private static final String HORIZONTAL_SHOWER =
            "horizontal_shower";
    private static final String DIAGONAL = "diagonal";
    private static final String DIAGONAL_SHOWER =
            "diagonal_shower";
    // 1.16 PlanetWavesGrab, next to the shared core grab chain.
    private static final String PW_GRAB = "planet_waves_grab";

    public static final DeferredRegister<AbilityType<?>>
            ABILITY_TYPES = DeferredRegister.create(
                    JojoRegistries.ABILITY_TYPES_REG,
                    MOD_ID);
    public static final DeferredRegister<EntityType<?>>
            ENTITY_TYPES = DeferredRegister.create(
                    Registries.ENTITY_TYPE,
                    MOD_ID);
    public static final DeferredRegister<MobEffect>
            MOB_EFFECTS = DeferredRegister.create(
                    Registries.MOB_EFFECT,
                    MOD_ID);
    public static final DeferredRegister<SoundEvent>
            SOUND_EVENTS = DeferredRegister.create(
                    Registries.SOUND_EVENT,
                    MOD_ID);
    public static final DeferredRegister<StandType>
            STAND_TYPES = DeferredRegister.create(
                    JojoRegistries.DEFAULT_STANDS_REG,
                    MOD_ID);

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<MeteorAttractionAbility>>
            VERTICAL_METEOR = meteorAbility(
                    VERTICAL, Trajectory.VERTICAL, false);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<MeteorAttractionAbility>>
            VERTICAL_METEOR_SHOWER = meteorAbility(
                    VERTICAL_SHOWER, Trajectory.VERTICAL, true);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<MeteorAttractionAbility>>
            HORIZONTAL_METEOR = meteorAbility(
                    HORIZONTAL, Trajectory.HORIZONTAL, false);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<MeteorAttractionAbility>>
            HORIZONTAL_METEOR_SHOWER = meteorAbility(
                    HORIZONTAL_SHOWER, Trajectory.HORIZONTAL, true);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<MeteorAttractionAbility>>
            DIAGONAL_METEOR = meteorAbility(
                    DIAGONAL, Trajectory.DIAGONAL, false);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<MeteorAttractionAbility>>
            DIAGONAL_METEOR_SHOWER = meteorAbility(
                    DIAGONAL_SHOWER, Trajectory.DIAGONAL, true);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<PlanetWavesGrabAbility>>
            PLANET_WAVES_GRAB_ABILITY = ABILITY_TYPES.register(
                    PW_GRAB,
                    key -> new AbilityType<>(
                            key, PlanetWavesGrabAbility::new));

    public static final DeferredHolder<
            EntityType<?>, EntityType<PlanetWavesStandEntity>>
            PLANET_WAVES_ENTITY = ENTITY_TYPES.register(
                    "pw",
                    key -> EntityType.Builder
                            .<PlanetWavesStandEntity>of(
                                    PlanetWavesStandEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(STAND_WIDTH, STAND_HEIGHT)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));
    public static final DeferredHolder<
            EntityType<?>, EntityType<MeteoriteEntity>>
            METEORITE = ENTITY_TYPES.register(
                    "meteorite",
                    key -> EntityType.Builder
                            .<MeteoriteEntity>of(
                                    MeteoriteEntity::new,
                                    MobCategory.MISC)
                            .fireImmune()
                            .sized(1.2F, 1.2F)
                            .clientTrackingRange(128)
                            .updateInterval(1)
                            .build(key.toString()));

    public static final DeferredHolder<MobEffect, TrueBlindnessEffect>
            TRUE_BLINDNESS = MOB_EFFECTS.register(
                    "true_blindness",
                    key -> new TrueBlindnessEffect());

    public static final DeferredHolder<SoundEvent, SoundEvent>
            PLANET_WAVES_SUMMON_VOICE =
                    sound("viviano_westwood_planet_waves");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            METEORITE_SUMMON =
                    sound("planet_waves_meteorite_summon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            PLANET_WAVES_GRAB =
                    sound("planet_waves_grab");
    public static final OstSoundList PLANET_WAVES_OST =
            new OstSoundList(id("planet_waves_ost"), SOUND_EVENTS);

    public static final DeferredHolder<StandType, EntityStandType>
            PLANET_WAVES = STAND_TYPES.register(
                    "pw", PlanetWavesAddon::createStand);

    public PlanetWavesAddon(IEventBus modEventBus) {
        requireCoreApi();
        ABILITY_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        MOB_EFFECTS.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        modEventBus.addListener(
                PlanetWavesEvents::registerAttributes);
        NeoForge.EVENT_BUS.addListener(
                MeteorAttractionAbility::tickDueShowerMeteors);
        NeoForge.EVENT_BUS.addListener(
                MeteorAttractionAbility::clearDueShowerMeteors);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static DeferredHolder<
            AbilityType<?>, AbilityType<MeteorAttractionAbility>>
            meteorAbility(
                    String id,
                    Trajectory trajectory,
                    boolean shower) {
        return ABILITY_TYPES.register(
                id,
                key -> new AbilityType<>(
                        key,
                        (type, abilityId) ->
                                new MeteorAttractionAbility(
                                        type,
                                        abilityId,
                                        trajectory,
                                        shower)));
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
                                .FEATURE_TIME_STOP_LIFECYCLE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ACTION_SUMMON_LOCK_HOOK_V1)
                // canFireReleasedHold (R146 core held-action rechecks)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ENTITY_ACTION_HOOKS_V1)) {
            throw new IllegalStateException(
                    "RotP Planet Waves requires ROTP addon ABI 1 "
                            + "with namespaced resources, Stand visual "
                            + "contexts, time-stop lifecycle, the summon "
                            + "lock hold count and entity action hooks.");
        }
    }

    private static EntityStandType createStand(ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addHumanoidStandStuff()
                .addAbility(PUNCH, ModStandAbilities.PUNCH)
                .addAbility(BARRAGE, ModStandAbilities.BARRAGE)
                .addAbility(
                        HEAVY_PUNCH,
                        ModStandAbilities.HEAVY_PUNCH)
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
                            punch.initIsGrabVariation();
                        })
                .addAbility(
                        "grab_barrage",
                        ModStandAbilities.BARRAGE,
                        barrage -> {
                            barrage.initIsGrabVariation();
                        })
                .addAbility(
                        "grab_heavy_punch",
                        ModStandAbilities.HEAVY_PUNCH,
                        punch -> {
                            punch.initIsGrabVariation();
                        })
                .addAbility(PW_GRAB, PLANET_WAVES_GRAB_ABILITY)
                .addAbility(GUARD, ModStandAbilities.GUARD)
                .addAbility(VERTICAL, VERTICAL_METEOR)
                .addAbility(
                        VERTICAL_SHOWER,
                        VERTICAL_METEOR_SHOWER)
                .addAbility(HORIZONTAL, HORIZONTAL_METEOR)
                .addAbility(
                        HORIZONTAL_SHOWER,
                        HORIZONTAL_METEOR_SHOWER)
                .addAbility(DIAGONAL, DIAGONAL_METEOR)
                .addAbility(
                        DIAGONAL_SHOWER,
                        DIAGONAL_METEOR_SHOWER)
                .makeControlScheme("hotbar")
                    .bind(
                            PUNCH,
                            InputMethod.CLICK,
                            InputKey.LMB)
                    .bind(
                            BARRAGE,
                            InputMethod.HOLD,
                            InputKey.LMB)
                    .bind(
                            HEAVY_PUNCH,
                            InputMethod.CLICK,
                            InputKey.RMB)
                    .bind(
                            GUARD,
                            InputMethod.HOLD,
                            InputKey.RMB)
                    .bind(
                            "grab",
                            InputMethod.CLICK,
                            MovesetBuilder.DEFAULT_GRAB_INPUT)
                    .bind(
                            "heavy_charged",
                            InputMethod.HOLD,
                            MovesetBuilder
                                    .DEFAULT_CHARGED_HEAVY_INPUT)
                    .bind(
                            "grab_throw",
                            InputMethod.HOLD,
                            MovesetBuilder
                                    .DEFAULT_GRAB_THROW_INPUT)
                    .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
                    // 1.16 left-click hotbar slot after the punch.
                    .addToHotbar(
                            PW_GRAB, 0, InputMethod.HOLD)
                    .addToHotbar(
                            VERTICAL, 0, InputMethod.HOLD)
                    .addHotbarSlotVariation(
                            VERTICAL_SHOWER,
                            VERTICAL,
                            InputKey.Modifier.SHIFT,
                            InputMethod.HOLD)
                    .addToHotbar(
                            HORIZONTAL, 0, InputMethod.HOLD)
                    .addHotbarSlotVariation(
                            HORIZONTAL_SHOWER,
                            HORIZONTAL,
                            InputKey.Modifier.SHIFT,
                            InputMethod.HOLD)
                    .addToHotbar(
                            DIAGONAL, 0, InputMethod.HOLD)
                    .addHotbarSlotVariation(
                            DIAGONAL_SHOWER,
                            DIAGONAL,
                            InputKey.Modifier.SHIFT,
                            InputMethod.HOLD)
                .finalizeControlScheme()
                .makeControlScheme("keybinds")
                    .bind(
                            PUNCH,
                            InputMethod.CLICK,
                            InputKey.LMB)
                    .bind(
                            BARRAGE,
                            InputMethod.HOLD,
                            InputKey.LMB)
                    .bind(
                            HEAVY_PUNCH,
                            InputMethod.CLICK,
                            InputKey.RMB)
                    .bind(
                            GUARD,
                            InputMethod.HOLD,
                            InputKey.RMB)
                    .bind(
                            "grab",
                            InputMethod.CLICK,
                            MovesetBuilder.DEFAULT_GRAB_INPUT)
                    .bind(
                            "heavy_charged",
                            InputMethod.HOLD,
                            MovesetBuilder
                                    .DEFAULT_CHARGED_HEAVY_INPUT)
                    .bind(
                            "grab_throw",
                            InputMethod.HOLD,
                            MovesetBuilder
                                    .DEFAULT_GRAB_THROW_INPUT)
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
                // Resolve level 1 unlocks it, as in 1.16.
                .addSkill(
                        StandUnlockableSkill.startingAbility(PW_GRAB))
                .addSkill(
                        StandUnlockableSkill.unlockableAbility(
                                        VERTICAL, 100)
                                .withAbility(VERTICAL_SHOWER))
                .addSkill(
                        StandUnlockableSkill.unlockableAbility(
                                        HORIZONTAL, 200)
                                .withAbility(HORIZONTAL_SHOWER)
                                .prerequisiteSkill(VERTICAL))
                .addSkill(
                        StandUnlockableSkill.unlockableAbility(
                                        DIAGONAL, 300)
                                .withAbility(DIAGONAL_SHOWER)
                                .prerequisiteSkill(HORIZONTAL))
                .addHumanoidStandSkills();

        return new EntityStandType(
                new StandStats.Builder()
                        .power(POWER)
                        .speed(SPEED)
                        .range(EFFECTIVE_RANGE, MAX_RANGE)
                        .durability(DURABILITY)
                        .precision(PRECISION)
                        .build(),
                moveset,
                PLANET_WAVES_ENTITY.get(),
                id)
                .standControlType(StandControlType.CLOSE_RANGE_DIRECT)
                .manualControl(true)
                .distanceStrengthDecay(true)
                .standDimensions(STAND_WIDTH, STAND_HEIGHT)
                .summonShout(() -> PLANET_WAVES_SUMMON_VOICE)
                .summonSound(() -> ModSoundEvents.STAND_SUMMON)
                .unsummonSound(() -> ModSoundEvents.STAND_UNSUMMON)
                .ost(() -> PLANET_WAVES_OST);
    }
}
