package rotp.addons.cmoonreimagined;

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

import rotp.addons.cmoonreimagined.ability.CMoonPunchAbility;
import rotp.addons.cmoonreimagined.ability.GravityChangeAbility;
import rotp.addons.cmoonreimagined.ability.InversionBarrageAbility;
import rotp.addons.cmoonreimagined.effect.ArmsInversionEffect;
import rotp.addons.cmoonreimagined.entity.CMoonStandEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(CMoonAddon.MOD_ID)
public final class CMoonAddon {
    public static final String MOD_ID = "cmoon_rm";
    public static final int REQUIRED_CORE_ABI = 1;
    public static final ResourceLocation BASE_C_MOON_ID =
            ResourceLocation.fromNamespaceAndPath(
                    "rotp_cm", "cmoon");

    public static final float STAND_WIDTH = 0.7F;
    public static final float STAND_HEIGHT = 2.0F;
    public static final double POWER = 10.0D;
    public static final double SPEED = 16.0D;
    public static final double EFFECTIVE_RANGE = 20.0D;
    public static final double MAX_RANGE = 20.0D;
    public static final double DURABILITY = 10.0D;
    public static final double PRECISION = 4.0D;

    public static final String PUNCH = "punch";
    public static final String BARRAGE = "barrage";
    public static final String HEAVY_PUNCH = "heavy_punch";
    public static final String FINISHER_PUNCH = "finisher_punch";
    public static final String GUARD = "guard";
    public static final String GRAVITY_CHANGE = "gravity_change";

    public static final DeferredRegister<AbilityType<?>>
            ABILITY_TYPES = DeferredRegister.create(
                    JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
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
            AbilityType<?>, AbilityType<CMoonPunchAbility>>
            C_MOON_PUNCH = ABILITY_TYPES.register(
                    PUNCH,
                    key -> new AbilityType<>(
                            key, CMoonPunchAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<InversionBarrageAbility>>
            C_MOON_BARRAGE = ABILITY_TYPES.register(
                    BARRAGE,
                    key -> new AbilityType<>(
                            key, InversionBarrageAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<GravityChangeAbility>>
            C_MOON_GRAVITY_CHANGE = ABILITY_TYPES.register(
                    GRAVITY_CHANGE,
                    key -> new AbilityType<>(
                            key, GravityChangeAbility::new));

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

    public static final DeferredHolder<
            MobEffect, ArmsInversionEffect>
            ARMS_INVERSION = MOB_EFFECTS.register(
                    "arms_inversion",
                    key -> new ArmsInversionEffect());

    public static final DeferredHolder<SoundEvent, SoundEvent>
            C_MOON_SUMMON_VOICELINE =
                    sound("cmoon_summon_voiceline");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            C_MOON_SUMMON_SOUND =
                    sound("cmoon_summon_sound");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            C_MOON_UNSUMMON_SOUND =
                    sound("cmoon_unsummon_sound");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            C_MOON_BARRAGE_SWING =
                    sound("cmoon_barrage_swing");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            C_MOON_GRAVITY_SHIFT_NORMAL =
                    sound("cmoon_gravity_shift_normal");
    public static final OstSoundList C_MOON_OST =
            new OstSoundList(id("cmoon_ost"), SOUND_EVENTS);

    public static final DeferredHolder<StandType, EntityStandType>
            C_MOON = STAND_TYPES.register(
                    "cmoon", CMoonAddon::createStand);

    public CMoonAddon(IEventBus modEventBus) {
        requireCoreApi();
        ABILITY_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        MOB_EFFECTS.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        modEventBus.addListener(CMoonModEvents::registerAttributes);
        modEventBus.addListener(CMoonAddon::commonSetup);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(
            String path) {
        return SOUND_EVENTS.register(
                path, SoundEvent::createVariableRangeEvent);
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            if (!JojoRegistries.DEFAULT_STANDS_REG.containsKey(
                    BASE_C_MOON_ID)) {
                throw new IllegalStateException(
                        "C-Moon Reimagined requires base Stand "
                                + BASE_C_MOON_ID);
            }
        });
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
                                .FEATURE_DIRECTIONAL_GRAVITY_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_DIRECTIONAL_GRAVITY_V2)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_TIME_STOP_LIFECYCLE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ADDON_POST_EFFECT_LIFECYCLE_V1)) {
            throw new IllegalStateException(
                    "C-Moon Reimagined requires ROTP addon ABI 1 "
                            + "and the base rotp_cm:cmoon Stand with "
                            + "namespaced ability resources and "
                            + "Stand visual contexts plus "
                            + "full directional gravity v2 and "
                            + "time/post-effect lifecycle contracts.");
        }
    }

    private static EntityStandType createStand(ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addHumanoidStandStuff()
                .addAbility(
                        PUNCH,
                        C_MOON_PUNCH,
                        punch -> punch.punchImpactSound(
                                ModSoundEvents.STAND_PUNCH_LIGHT))
                .addAbility(
                        BARRAGE,
                        C_MOON_BARRAGE,
                        // 1.16 standSound(PERFORM, CMOON_BARRAGE_SWING) also cried
                        // with the arms only.
                        barrage -> barrage
                                .barrageHitSound(
                                        ModSoundEvents
                                                .STAND_PUNCH_LIGHT)
                                .barrageCrySound(
                                        C_MOON_BARRAGE_SWING)
                                .barrageCryInArmsOnly())
                .addAbility(
                        HEAVY_PUNCH,
                        ModStandAbilities.HEAVY_PUNCH)
                .addAbility(
                        "heavy_charged",
                        ModStandAbilities.HEAVY_CHARGED)
                // 1.16 CMOON_FINISHER_PUNCH: heavy punch finisher variation, no Resolve gate
                .addAbility(
                        FINISHER_PUNCH,
                        ModStandAbilities.HEAVY_PUNCH,
                        punch -> {
                            punch.heavyPunchImpactSound(
                                    ModSoundEvents.STAND_PUNCH_HEAVY);
                            punch.initIsFinisher(HEAVY_PUNCH);
                        })
                .addAbility(GUARD, ModStandAbilities.GUARD)
                .addAbility(
                        GRAVITY_CHANGE,
                        C_MOON_GRAVITY_CHANGE)
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
                            GRAVITY_CHANGE,
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
                                        "heavy_charged")
                                .prerequisiteSkill(HEAVY_PUNCH))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                        FINISHER_PUNCH)
                                .prerequisiteSkill(HEAVY_PUNCH))
                .addSkill(
                        StandUnlockableSkill.startingAbility(GUARD))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                GRAVITY_CHANGE))
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
                C_MOON_ENTITY.get(),
                id)
                .standControlType(
                        StandControlType.LONG_DISTANCE_OPERATION)
                .manualControl(true)
                .distanceStrengthDecay(false)
                .standDimensions(STAND_WIDTH, STAND_HEIGHT)
                .summonShout(() -> C_MOON_SUMMON_VOICELINE)
                .summonSound(() -> C_MOON_SUMMON_SOUND)
                .unsummonSound(() -> C_MOON_UNSUMMON_SOUND)
                .ost(() -> C_MOON_OST);
    }
}
