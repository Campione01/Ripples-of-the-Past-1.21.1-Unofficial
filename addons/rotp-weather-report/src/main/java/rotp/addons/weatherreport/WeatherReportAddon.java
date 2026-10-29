package rotp.addons.weatherreport;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import rotp.core.api.RotpAddonApi;
import rotp.core.core.JojoRegistries;
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

import rotp.addons.weatherreport.ability.ChangeWeatherAbility;
import rotp.addons.weatherreport.ability.WeatherReportCloudShieldAbility;
import rotp.addons.weatherreport.ability.WeatherReportColdWindAbility;
import rotp.addons.weatherreport.ability.WeatherReportBarrageAbility;
import rotp.addons.weatherreport.ability.WeatherReportFinisherAbility;
import rotp.addons.weatherreport.ability.WeatherReportFuguRainAbility;
import rotp.addons.weatherreport.ability.WeatherReportGiveCloudShieldAbility;
import rotp.addons.weatherreport.ability.WeatherReportHeavyPunchAbility;
import rotp.addons.weatherreport.ability.WeatherReportIcicleStrikeAbility;
import rotp.addons.weatherreport.ability.WeatherReportLightningAbility;
import rotp.addons.weatherreport.ability.WeatherReportPunchAbility;
import rotp.addons.weatherreport.ability.WeatherReportWindAbility;
import rotp.addons.weatherreport.block.BloodPuddleBlock;
import rotp.addons.weatherreport.block.BloodSpikesBlock;
import rotp.addons.weatherreport.effect.CloudShieldEffect;
import rotp.addons.weatherreport.effect.LegacyBleedingEffect;
import rotp.addons.weatherreport.entity.WeatherReportIcicleEntity;
import rotp.addons.weatherreport.entity.WeatherReportFugaProjectileEntity;
import rotp.addons.weatherreport.entity.WeatherReportFugaSummonerEntity;
import rotp.addons.weatherreport.entity.WeatherReportStandEntity;
import rotp.addons.weatherreport.network.WeatherReportPayloads;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(WeatherReportAddon.MOD_ID)
public final class WeatherReportAddon {
    public static final String MOD_ID = "rotp_wr";
    public static final String CHANGE_WEATHER_NAME = "change_weather";
    public static final String CLOUD_SHIELD_NAME = "cloud_shield";
    public static final String GIVE_CLOUD_SHIELD_NAME =
            "give_cloud_shield";
    public static final String WIND_NAME = "wind";
    public static final String COLD_WIND_NAME = "cold_wind";
    public static final String LIGHTNING_NAME = "lightning";
    public static final String ICICLE_STRIKE_NAME = "icicle_strike";
    public static final String FUGU_RAIN_NAME = "fugu_rain";

    public static final float STAND_WIDTH = 0.65F;
    public static final float STAND_HEIGHT = 1.95F;
    public static final double POWER = 14.0D;
    public static final double SPEED = 12.0D;
    public static final double EFFECTIVE_RANGE = 2.0D;
    public static final double MAX_RANGE = 10.0D;
    public static final double DURABILITY = 16.0D;
    public static final double PRECISION = 4.0D;
    public static final int CHANGE_WEATHER_SKILL_COST = 250;

    private static final int REQUIRED_CORE_ABI = 1;

    public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES =
            DeferredRegister.create(JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<StandType> STAND_TYPES =
            DeferredRegister.create(JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<WeatherReportPunchAbility>>
            WEATHER_REPORT_PUNCH = ABILITY_TYPES.register(
                    "weather_report_punch",
                    key -> new AbilityType<>(
                            key, WeatherReportPunchAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<WeatherReportBarrageAbility>>
            WEATHER_REPORT_BARRAGE = ABILITY_TYPES.register(
                    "weather_report_barrage",
                    key -> new AbilityType<>(
                            key, WeatherReportBarrageAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<WeatherReportHeavyPunchAbility>>
            WEATHER_REPORT_HEAVY_PUNCH = ABILITY_TYPES.register(
                    "weather_report_heavy_punch",
                    key -> new AbilityType<>(
                            key, WeatherReportHeavyPunchAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<WeatherReportFinisherAbility>>
            WEATHER_REPORT_FINISHER = ABILITY_TYPES.register(
                    "weather_report_finisher",
                    key -> new AbilityType<>(
                            key, WeatherReportFinisherAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<ChangeWeatherAbility>>
            WEATHER_REPORT_CHANGE_WEATHER = ABILITY_TYPES.register(
                    "weather_report_change_weather",
                    key -> new AbilityType<>(
                            key, ChangeWeatherAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<WeatherReportCloudShieldAbility>>
            WEATHER_REPORT_CLOUD_SHIELD = ABILITY_TYPES.register(
                    "weather_report_cloud_shield",
                    key -> new AbilityType<>(
                            key,
                            WeatherReportCloudShieldAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<WeatherReportGiveCloudShieldAbility>>
            WEATHER_REPORT_GIVE_CLOUD_SHIELD =
                    ABILITY_TYPES.register(
                            "weather_report_give_cloud_shield",
                            key -> new AbilityType<>(
                                    key,
                                    WeatherReportGiveCloudShieldAbility
                                            ::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<WeatherReportWindAbility>>
            WEATHER_REPORT_WIND = ABILITY_TYPES.register(
                    "weather_report_wind",
                    key -> new AbilityType<>(
                            key, WeatherReportWindAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<WeatherReportColdWindAbility>>
            WEATHER_REPORT_COLD_WIND = ABILITY_TYPES.register(
                    "weather_report_cold_wind",
                    key -> new AbilityType<>(
                            key,
                            WeatherReportColdWindAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<WeatherReportLightningAbility>>
            WEATHER_REPORT_LIGHTNING = ABILITY_TYPES.register(
                    "weather_report_lightning",
                    key -> new AbilityType<>(
                            key,
                            WeatherReportLightningAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<WeatherReportIcicleStrikeAbility>>
            WEATHER_REPORT_ICICLE_STRIKE =
                    ABILITY_TYPES.register(
                            "weather_report_icicle_strike",
                            key -> new AbilityType<>(
                                    key,
                                    WeatherReportIcicleStrikeAbility
                                            ::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<WeatherReportFuguRainAbility>>
            WEATHER_REPORT_FUGU_RAIN =
                    ABILITY_TYPES.register(
                            "weather_report_fugu_rain",
                            key -> new AbilityType<>(
                                    key,
                                    WeatherReportFuguRainAbility
                                            ::new));

    public static final DeferredHolder<MobEffect, CloudShieldEffect>
            CLOUD_SHIELD_EFFECT = MOB_EFFECTS.register(
                    "cloud_shield_effect",
                    key -> new CloudShieldEffect());
    public static final DeferredHolder<MobEffect, LegacyBleedingEffect>
            BLEEDING = MOB_EFFECTS.register(
                    "bleeding",
                    key -> new LegacyBleedingEffect());

    public static final DeferredBlock<BloodPuddleBlock>
            BLOOD_PUDDLE = BLOCKS.register(
                    "blood_puddle",
                    () -> new BloodPuddleBlock(
                            Block.Properties.of()
                                    .mapColor(MapColor.COLOR_RED)
                                    .strength(0.1F)
                                    .noCollission()
                                    .noOcclusion()
                                    .noLootTable()
                                    .sound(SoundType.SLIME_BLOCK)
                                    .pushReaction(
                                            PushReaction.DESTROY)));
    public static final DeferredBlock<BloodSpikesBlock>
            BLOOD_SPIKES = BLOCKS.register(
                    "blood_spikes",
                    () -> new BloodSpikesBlock(
                            Block.Properties.of()
                                    .mapColor(MapColor.COLOR_RED)
                                    .strength(0.75F)
                                    .noCollission()
                                    .noOcclusion()
                                    .noLootTable()
                                    .sound(SoundType.GLASS)
                                    .pushReaction(
                                            PushReaction.DESTROY)));

    public static final DeferredHolder<
            EntityType<?>, EntityType<WeatherReportStandEntity>>
            WEATHER_REPORT_ENTITY = ENTITY_TYPES.register(
                    "weather_report",
                    key -> EntityType.Builder
                            .<WeatherReportStandEntity>of(
                                    WeatherReportStandEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(STAND_WIDTH, STAND_HEIGHT)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));
    public static final DeferredHolder<
            EntityType<?>, EntityType<WeatherReportIcicleEntity>>
            ICICLE_STRIKE_PROJECTILE = ENTITY_TYPES.register(
                    "icicle_strike_projectile",
                    key -> EntityType.Builder
                            .<WeatherReportIcicleEntity>of(
                                    WeatherReportIcicleEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(32)
                            .updateInterval(20)
                            .build(key.toString()));
    public static final DeferredHolder<
            EntityType<?>,
            EntityType<WeatherReportFugaSummonerEntity>>
            FUGA_SUMMONER = ENTITY_TYPES.register(
                    "fuga_summoner",
                    key -> EntityType.Builder
                            .<WeatherReportFugaSummonerEntity>of(
                                    WeatherReportFugaSummonerEntity::new,
                                    MobCategory.MISC)
                            .noSummon()
                            .sized(0.01F, 0.01F)
                            .clientTrackingRange(32)
                            .updateInterval(20)
                            .build(key.toString()));
    public static final DeferredHolder<
            EntityType<?>,
            EntityType<WeatherReportFugaProjectileEntity>>
            FUGA_PROJECTILE = ENTITY_TYPES.register(
                    "fuga_projectile",
                    key -> EntityType.Builder
                            .<WeatherReportFugaProjectileEntity>of(
                                    WeatherReportFugaProjectileEntity::new,
                                    MobCategory.MISC)
                            .noSummon()
                            .sized(1.5F, 1.5F)
                            .clientTrackingRange(64)
                            .updateInterval(10)
                            .build(key.toString()));

    public static final DeferredHolder<SoundEvent, SoundEvent>
            WEATHER_REPORT_CALL = sound("weather_report");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            WEATHER_REPORT_USER_RETRACT =
                    sound("weather_report_user_retract");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            WEATHER_REPORT_SUMMON = sound("weather_report_summon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            WEATHER_REPORT_UNSUMMON = sound("weather_report_unsummon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            WEATHER_REPORT_SWING = sound("weather_report_swing_sound");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            WEATHER_REPORT_PUNCH_SOUND = sound("weather_report_punch");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            WEATHER_REPORT_HEAVY_PUNCH_SOUND =
            sound("weather_report_heavy_punch");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            WEATHER_REPORT_FINISHER_SOUND =
            sound("weather_report_finisher");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            WEATHER_REPORT_USER_HEAVY_PUNCH =
            sound("weather_report_user_heavy_punch");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            WEATHER_REPORT_USER_LIGHT_PUNCH =
            sound("weather_report_user_light_punch");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            WEATHER_REPORT_CHANGE_WEATHER_SOUND =
            sound("weather_report_change_weather_sound");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            WEATHER_REPORT_CLOUD_SHIELD_SOUND =
            sound("weather_report_cloud_shield");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            WEATHER_REPORT_USER_CLOUD_SHIELD =
            sound("weather_report_user_cloud_shield_cast");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            WEATHER_REPORT_WIND_SOUND =
            sound("weather_report_wind_sound");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            WEATHER_REPORT_COLD_WIND_SOUND =
            sound("weather_report_cold_wind_sound");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            WEATHER_REPORT_LIGHTNING_CAST =
            sound("weather_report_lightning_cast");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            WEATHER_REPORT_USER_LIGHTNING =
            sound("weather_report_user_lightning_cast");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            WEATHER_REPORT_USER_FUGU_RAIN =
            sound("weather_report_user_fugu_rain_sound");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            WEATHER_REPORT_FUGU_CAST =
            sound("weather_report_fugu_cast_sound");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            WEATHER_REPORT_FUGU_SPLASH =
            sound("weather_report_fugu_splash_sound");
    public static final OstSoundList WEATHER_REPORT_OST =
            new OstSoundList(id("weather_report_ost"), SOUND_EVENTS);

    public static final DeferredHolder<StandType, EntityStandType>
            WEATHER_REPORT = STAND_TYPES.register(
                    "weather_report", WeatherReportAddon::createStand);

    public WeatherReportAddon(IEventBus modEventBus) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_VISUAL_CONTEXT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_SHOUTS_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_POWER_ACTION_SLOT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_ACTION_PORT_SLOT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ACTION_SUMMON_LOCK_HOOK_V1)) {
            throw new IllegalStateException(
                    "RotP Weather Report requires ROTP addon ABI 1 with "
                            + "namespaced ability resources, Stand visual "
                            + "contexts, Stand action port slots and the "
                            + "summon lock hold count.");
        }

        ABILITY_TYPES.register(modEventBus);
        BLOCKS.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        MOB_EFFECTS.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        modEventBus.addListener(WeatherReportPayloads::register);
        modEventBus.addListener(
                WeatherReportEvents::registerAttributes);
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
                        "punch",
                        WEATHER_REPORT_PUNCH,
                        punch -> punch
                                .userShoutSound(
                                        WEATHER_REPORT_USER_LIGHT_PUNCH)
                                .punchImpactSound(
                                        WEATHER_REPORT_PUNCH_SOUND))
                .addAbility(
                        "barrage",
                        WEATHER_REPORT_BARRAGE,
                        barrage -> barrage.barrageHitSound(
                                WEATHER_REPORT_PUNCH_SOUND))
                .addAbility(
                        "heavy_punch",
                        WEATHER_REPORT_HEAVY_PUNCH,
                        punch -> punch
                                .userShoutSound(
                                        WEATHER_REPORT_USER_HEAVY_PUNCH)
                                .heavyPunchImpactSound(
                                        WEATHER_REPORT_HEAVY_PUNCH_SOUND))
                .addAbility(
                        "heavy_charged",
                        ModStandAbilities.HEAVY_CHARGED)
                .addAbility(
                        "finisher_punch",
                        WEATHER_REPORT_FINISHER,
                        punch -> {
                            punch.heavyPunchImpactSound(
                                    WEATHER_REPORT_FINISHER_SOUND);
                            punch.initIsFinisher("heavy_punch");
                        })
                .addAbility("grab", ModStandAbilities.GRAB)
                .addAbility("grab_throw", ModStandAbilities.GRAB_THROW)
                .addAbility(
                        "grab_punch",
                        WEATHER_REPORT_PUNCH,
                        punch -> {
                            punch.userShoutSound(
                                    WEATHER_REPORT_USER_LIGHT_PUNCH);
                            punch.punchImpactSound(
                                    WEATHER_REPORT_PUNCH_SOUND);
                            punch.initIsGrabVariation();
                        })
                .addAbility(
                        "grab_barrage",
                        WEATHER_REPORT_BARRAGE,
                        barrage -> {
                            barrage.barrageHitSound(
                                    WEATHER_REPORT_PUNCH_SOUND);
                            barrage.initIsGrabVariation();
                        })
                .addAbility(
                        "grab_heavy_punch",
                        WEATHER_REPORT_HEAVY_PUNCH,
                        punch -> {
                            punch.userShoutSound(
                                    WEATHER_REPORT_USER_HEAVY_PUNCH);
                            punch.heavyPunchImpactSound(
                                    WEATHER_REPORT_HEAVY_PUNCH_SOUND);
                            punch.initIsGrabVariation();
                        })
                .addAbility("guard", ModStandAbilities.GUARD)
                .addAbility(
                        CHANGE_WEATHER_NAME,
                        WEATHER_REPORT_CHANGE_WEATHER,
                        ability -> ability.resolveLevelToUnlock(3))
                .addAbility(
                        CLOUD_SHIELD_NAME,
                        WEATHER_REPORT_CLOUD_SHIELD,
                        ability -> ability.userShoutSound(
                                WEATHER_REPORT_USER_CLOUD_SHIELD))
                .addAbility(
                        GIVE_CLOUD_SHIELD_NAME,
                        WEATHER_REPORT_GIVE_CLOUD_SHIELD)
                .addAbility(WIND_NAME, WEATHER_REPORT_WIND)
                .addAbility(
                        COLD_WIND_NAME,
                        WEATHER_REPORT_COLD_WIND,
                        ability -> ability.resolveLevelToUnlock(1))
                .addAbility(
                        LIGHTNING_NAME,
                        WEATHER_REPORT_LIGHTNING,
                        ability -> ability.resolveLevelToUnlock(2))
                .addAbility(
                        ICICLE_STRIKE_NAME,
                        WEATHER_REPORT_ICICLE_STRIKE,
                        ability -> ability.resolveLevelToUnlock(2))
                .addAbility(
                        FUGU_RAIN_NAME,
                        WEATHER_REPORT_FUGU_RAIN,
                        ability -> ability.resolveLevelToUnlock(3))
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
                            CHANGE_WEATHER_NAME,
                            0,
                            InputMethod.HOLD)
                    .addToHotbar(
                            CLOUD_SHIELD_NAME,
                            0,
                            InputMethod.HOLD)
                    .addToHotbar(
                            GIVE_CLOUD_SHIELD_NAME,
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            WIND_NAME,
                            0,
                            InputMethod.HOLD)
                    .addToHotbar(
                            COLD_WIND_NAME,
                            0,
                            InputMethod.HOLD)
                    .addToHotbar(
                            LIGHTNING_NAME,
                            0,
                            InputMethod.HOLD)
                    .addToHotbar(
                            ICICLE_STRIKE_NAME,
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            FUGU_RAIN_NAME,
                            0,
                            InputMethod.HOLD)
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
                .addSkill(StandUnlockableSkill.startingAbility(
                        "finisher_punch"))
                .addSkill(StandUnlockableSkill.startingAbility("guard"))
                .addSkill(StandUnlockableSkill.startingAbility("grab"))
                .addSkill(StandUnlockableSkill.unlockableAbility(
                                "grab_throw", 100)
                        .withAbility(
                                "grab_punch",
                                "grab_barrage",
                                "grab_heavy_punch")
                        .prerequisiteSkill("grab"))
                .addSkill(StandUnlockableSkill.unlockableAbility(
                        CHANGE_WEATHER_NAME,
                        CHANGE_WEATHER_SKILL_COST))
                .addSkill(StandUnlockableSkill.startingAbility(
                                CLOUD_SHIELD_NAME)
                        .withAbility(GIVE_CLOUD_SHIELD_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                                WIND_NAME)
                        .withAbility(COLD_WIND_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        LIGHTNING_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        ICICLE_STRIKE_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        FUGU_RAIN_NAME))
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
                WEATHER_REPORT_ENTITY.get(),
                id)
                .standControlType(StandControlType.CLOSE_RANGE_DIRECT)
                .manualControl(true)
                .distanceStrengthDecay(true)
                .standDimensions(STAND_WIDTH, STAND_HEIGHT)
                .summonShout(() -> WEATHER_REPORT_CALL)
                .summonSound(() -> WEATHER_REPORT_SUMMON)
                .unsummonSound(() -> WEATHER_REPORT_UNSUMMON)
                .ost(() -> WEATHER_REPORT_OST);
    }
}
