package rotp.addons.spicegirl;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import java.util.function.Supplier;

import rotp.core.api.RotpAddonApi;
import rotp.core.api.block.EntitySoftLandingProviders;
import rotp.core.core.JojoRegistries;
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
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.util.sound.OstSoundList;

import rotp.addons.spicegirl.ability.ElasticLeapAbility;
import rotp.addons.spicegirl.ability.ElasticLiftAbility;
import rotp.addons.spicegirl.ability.SoftenAreaAbility;
import rotp.addons.spicegirl.ability.SoftenSelfAbility;
import rotp.addons.spicegirl.ability.SoftenTargetAbility;
import rotp.addons.spicegirl.ability.SpiceGirlFinisherPunchAbility;
import rotp.addons.spicegirl.ability.SpiceGirlHeavyPunchAbility;
import rotp.addons.spicegirl.ability.WallClimbAbility;
import rotp.addons.spicegirl.effect.ElasticEffect;
import rotp.addons.spicegirl.entity.SpiceGirlStandEntity;
import rotp.addons.spicegirl.state.ElasticSourceState;
import rotp.addons.spicegirl.state.ElasticTargetState;
import rotp.addons.spicegirl.state.SoftAreaIndexState;
import rotp.addons.spicegirl.state.SoftAreaState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@Mod(SpiceGirlAddon.MOD_ID)
public final class SpiceGirlAddon {
    public static final String MOD_ID = "rotp_sg";

    public static final double POWER = 14.0D;
    public static final double SPEED = 15.0D;
    public static final double EFFECTIVE_RANGE = 5.0D;
    public static final double MAX_RANGE = 10.0D;
    public static final double DURABILITY = 13.0D;
    public static final double PRECISION = 7.0D;
    public static final float STAND_WIDTH = 0.7F;
    public static final float STAND_HEIGHT = 2.1F;

    private static final int REQUIRED_CORE_ABI = 1;

    public static final String PUNCH = "punch";
    public static final String BARRAGE = "barrage";
    public static final String HEAVY_PUNCH = "heavy_punch";
    public static final String FINISHER_PUNCH = "finisher_punch";
    public static final String GUARD = "guard";
    public static final String SOFTEN_TARGET = "soften_target";
    public static final String SOFTEN_SELF = "soften_self";
    public static final String ELASTIC_LIFT = "elastic_lift";
    public static final String ELASTIC_LEAP = "elastic_leap";
    public static final String SOFTEN_AREA = "soften_area";
    public static final String WALL_CLIMB = "wall_climb";

    public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES =
            DeferredRegister.create(
                    JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<AttachmentType<?>>
            ATTACHMENT_TYPES = DeferredRegister.create(
                    NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<StandType> STAND_TYPES =
            DeferredRegister.create(
                    JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<SpiceGirlHeavyPunchAbility>>
            SPICE_GIRL_HEAVY_PUNCH = ABILITY_TYPES.register(
                    "spice_girl_heavy_punch",
                    key -> new AbilityType<>(
                            key, SpiceGirlHeavyPunchAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<SpiceGirlFinisherPunchAbility>>
            SPICE_GIRL_FINISHER_PUNCH = ABILITY_TYPES.register(
                    "spice_girl_finisher_punch",
                    key -> new AbilityType<>(
                            key, SpiceGirlFinisherPunchAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<SoftenAreaAbility>>
            SPICE_GIRL_SOFT_BLOCK = ABILITY_TYPES.register(
                    "spice_girl_soft_block",
                    key -> new AbilityType<>(key, SoftenAreaAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<WallClimbAbility>>
            SPICE_GIRL_WALL_CLIMB = ABILITY_TYPES.register(
                    "spice_girl_wall_climb",
                    key -> new AbilityType<>(key, WallClimbAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<SoftenTargetAbility>>
            SPICE_GIRL_BOUNCE = ABILITY_TYPES.register(
                    "spice_girl_bounce",
                    key -> new AbilityType<>(
                            key, SoftenTargetAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<SoftenSelfAbility>>
            SPICE_GIRL_BOUNCE_HER = ABILITY_TYPES.register(
                    "spice_girl_bounce_her",
                    key -> new AbilityType<>(
                            key, SoftenSelfAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<ElasticLiftAbility>>
            SPICE_GIRL_BOUNCE_UP = ABILITY_TYPES.register(
                    "spice_girl_bounce_up",
                    key -> new AbilityType<>(
                            key, ElasticLiftAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<ElasticLeapAbility>>
            SPICE_GIRL_BOUNCE_UP_HER = ABILITY_TYPES.register(
                    "spice_girl_bounce_up_her",
                    key -> new AbilityType<>(
                            key, ElasticLeapAbility::new));

    public static final Supplier<AttachmentType<ElasticSourceState>>
            ELASTIC_SOURCE = ATTACHMENT_TYPES.register(
                    "elastic_source",
                    () -> AttachmentType.builder(
                            ElasticSourceState::new).build());
    public static final Supplier<AttachmentType<ElasticTargetState>>
            ELASTIC_TARGET = ATTACHMENT_TYPES.register(
                    "elastic_target",
                    () -> AttachmentType.builder(
                            ElasticTargetState::new).build());
    public static final Supplier<AttachmentType<SoftAreaState>>
            SOFT_AREA = ATTACHMENT_TYPES.register(
                    "soft_area",
                    () -> AttachmentType.builder(
                            SoftAreaState::new).build());
    public static final Supplier<AttachmentType<SoftAreaIndexState.World>>
            SOFT_AREA_INDEX = ATTACHMENT_TYPES.register(
                    "soft_area_index",
                    () -> AttachmentType.builder(
                            SoftAreaIndexState.World::new).build());
    public static final DeferredHolder<
            EntityType<?>, EntityType<SpiceGirlStandEntity>>
            SPICE_GIRL_ENTITY = ENTITY_TYPES.register(
                    "spice_girl",
                    key -> EntityType.Builder
                            .<SpiceGirlStandEntity>of(
                                    SpiceGirlStandEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(STAND_WIDTH, STAND_HEIGHT)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<MobEffect, ElasticEffect> BOUNCE =
            MOB_EFFECTS.register("bounce", ElasticEffect::new);

    public static final DeferredHolder<SoundEvent, SoundEvent>
            SG_SUMMON = sound("sg_summon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SG_UNSUMMON = sound("sg_unsummon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SG_PUNCH = sound("sg_punch");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SG_PUNCH_HEAVY = sound("sg_punch_heavy");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SG_FINISHER = sound("sg_finisher");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SG_ABILITY_1 = sound("sg_ability_1");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SG_ABILITY_2 = sound("sg_ability_2");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SG_PUNCH_VOICE = sound("sg_punch_voice");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SG_BARRAGE_VOICE = sound("sg_barrage_voice");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SG_PUNCH_HEAVY_VOICE = sound("sg_punch_heavy_voice");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SG_PUNCH_FINISHER_VOICE =
                    sound("sg_punch_finisher_voice");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SG_SUMMON_VOICE = sound("sg_summon_voice");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SG_ABILITY1_VOICE = sound("sg_ability1_voice");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SG_ABILITY2_VOICE = sound("sg_ability2_voice");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            TRISH_ARIVIDERCHI = sound("trish_arividerchi");
    public static final OstSoundList SPICE_GIRL_OST =
            new OstSoundList(id("spice_girl_ost"), SOUND_EVENTS);

    public static final DeferredHolder<StandType, EntityStandType>
            SPICE_GIRL = STAND_TYPES.register(
                    "spice_girl", SpiceGirlAddon::createStand);

    public SpiceGirlAddon(IEventBus modEventBus) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_VISUAL_CONTEXT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ENTITY_SOFT_LANDING_PROVIDERS_V1)
                // canFireReleasedHold (R146 core held-action rechecks)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ENTITY_ACTION_HOOKS_V1)
                // 1.16 action shouts follow the sneak rule
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_SHOUTS_V1)) {
            throw new IllegalStateException(
                    "RotP Spice Girl requires ROTP addon ABI 1 with "
                            + "namespaced ability resources and Stand "
                            + "visual contexts plus all-entity soft "
                            + "landing providers and entity action "
                            + "hooks.");
        }

        ABILITY_TYPES.register(modEventBus);
        ATTACHMENT_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        MOB_EFFECTS.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        EntitySoftLandingProviders.register(
                id("spice_girl_soft_area_landing"),
                SpiceGirlSoftLandingProvider.INSTANCE);
        modEventBus.addListener(
                SpiceGirlModEvents::registerAttributes);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static ElasticSourceState sourceState(
            LivingEntity entity) {
        return entity.getData(ELASTIC_SOURCE);
    }

    public static ElasticTargetState targetState(
            LivingEntity entity) {
        return entity.getData(ELASTIC_TARGET);
    }

    public static SoftAreaState softAreaState(
            LivingEntity entity) {
        return entity.getData(SOFT_AREA);
    }

    public static SoftAreaIndexState.World softAreaIndex(
            ServerLevel level) {
        return level.getData(SOFT_AREA_INDEX);
    }

    public static boolean isSpiceGirlUser(
            LivingEntity entity) {
        StandPower power = entity != null ? StandPower.get(entity) : null;
        return power != null
                && power.hasPower()
                && power.getPowerType() == SPICE_GIRL.get();
    }

    public static SpiceGirlStandEntity activeStand(
            LivingEntity entity) {
        StandPower power = entity != null ? StandPower.get(entity) : null;
        return power != null
                && power.hasPower()
                && power.isSummoned()
                && power.getPowerType() == SPICE_GIRL.get()
                && power.getSummonedStandEntity()
                        instanceof SpiceGirlStandEntity stand
                && stand.isAlive()
                && !stand.isRemoved()
                ? stand
                : null;
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(
            String path) {
        return SOUND_EVENTS.register(
                path, SoundEvent::createVariableRangeEvent);
    }

    private static EntityStandType createStand(
            ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addHumanoidStandStuff()
                .addAbility(
                        PUNCH,
                        ModStandAbilities.PUNCH,
                        punch -> punch
                                .punchImpactSound(SG_PUNCH)
                                .standCrySound(SG_PUNCH_VOICE))
                .addAbility(
                        BARRAGE,
                        ModStandAbilities.BARRAGE,
                        barrage -> barrage.barrageCrySound(
                                SG_BARRAGE_VOICE))
                .addAbility(
                        HEAVY_PUNCH,
                        SPICE_GIRL_HEAVY_PUNCH,
                        punch -> punch
                                .heavyPunchImpactSound(
                                        SG_PUNCH_HEAVY))
                // 1.16 heavy / finisher voices are user shouts
                .addAbility(
                        "heavy_charged",
                        ModStandAbilities.HEAVY_CHARGED)
                // 1.16 (067 InitStands :66-67): no Resolve gate, so the
                // finisher and its meter open at Resolve 0
                .addAbility(
                        FINISHER_PUNCH,
                        SPICE_GIRL_FINISHER_PUNCH,
                        punch -> {
                            punch.heavyPunchImpactSound(
                                    SG_FINISHER);
                            punch.initIsFinisher(HEAVY_PUNCH);
                        })
                .addAbility("grab", ModStandAbilities.GRAB)
                .addAbility(
                        "grab_throw",
                        ModStandAbilities.GRAB_THROW)
                .addAbility(
                        "grab_punch",
                        ModStandAbilities.PUNCH,
                        punch -> {
                            punch.punchImpactSound(SG_PUNCH)
                                    .standCrySound(
                                            SG_PUNCH_VOICE);
                            punch.initIsGrabVariation();
                        })
                .addAbility(
                        "grab_barrage",
                        ModStandAbilities.BARRAGE,
                        barrage -> {
                            barrage.barrageCrySound(
                                    SG_BARRAGE_VOICE);
                            barrage.initIsGrabVariation();
                        })
                .addAbility(
                        "grab_heavy_punch",
                        SPICE_GIRL_HEAVY_PUNCH,
                        // voice comes from the type's user shout only
                        punch -> {
                            punch.heavyPunchImpactSound(
                                    SG_PUNCH_HEAVY);
                            punch.initIsGrabVariation();
                        })
                .addAbility(GUARD, ModStandAbilities.GUARD)
                .addAbility(SOFTEN_TARGET, SPICE_GIRL_BOUNCE)
                // 1.16 Resolve gates: bounce_her 1, bounce_up 2,
                // bounce_up_her 3, soft_block 1
                .addAbility(
                        SOFTEN_SELF,
                        SPICE_GIRL_BOUNCE_HER,
                        ability -> ability.resolveLevelToUnlock(1))
                .addAbility(
                        ELASTIC_LIFT,
                        SPICE_GIRL_BOUNCE_UP,
                        ability -> ability.resolveLevelToUnlock(2))
                .addAbility(
                        ELASTIC_LEAP,
                        SPICE_GIRL_BOUNCE_UP_HER,
                        ability -> ability.resolveLevelToUnlock(3))
                .addAbility(
                        SOFTEN_AREA,
                        SPICE_GIRL_SOFT_BLOCK,
                        ability -> ability.resolveLevelToUnlock(1))
                .addAbility(WALL_CLIMB, SPICE_GIRL_WALL_CLIMB)
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
                            SOFTEN_TARGET,
                            0,
                            InputMethod.CLICK)
                    .addHotbarSlotVariation(
                            SOFTEN_SELF,
                            SOFTEN_TARGET,
                            InputKey.Modifier.SHIFT,
                            InputMethod.HOLD)
                    .addToHotbar(
                            ELASTIC_LIFT,
                            0,
                            InputMethod.HOLD)
                    .addHotbarSlotVariation(
                            ELASTIC_LEAP,
                            ELASTIC_LIFT,
                            InputKey.Modifier.SHIFT,
                            InputMethod.HOLD)
                    .addToHotbar(
                            SOFTEN_AREA,
                            0,
                            InputMethod.HOLD)
                    .addToHotbar(
                            WALL_CLIMB,
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
                        StandUnlockableSkill.startingAbility(
                                FINISHER_PUNCH))
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
                                SOFTEN_TARGET))
                .addSkill(
                        StandUnlockableSkill.unlockableAbility(
                                        SOFTEN_SELF, 150)
                                .prerequisiteSkill(SOFTEN_TARGET))
                .addSkill(
                        StandUnlockableSkill.unlockableAbility(
                                        SOFTEN_AREA, 200)
                                .prerequisiteSkill(SOFTEN_TARGET))
                .addSkill(
                        StandUnlockableSkill.unlockableAbility(
                                ELASTIC_LIFT, 200))
                .addSkill(
                        StandUnlockableSkill.unlockableAbility(
                                        ELASTIC_LEAP, 300)
                                .prerequisiteSkill(ELASTIC_LIFT))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                WALL_CLIMB))
                .addHumanoidStandSkills();

        return new EntityStandType(
                new StandStats.Builder()
                        .power(POWER)
                        .speed(SPEED)
                        .range(EFFECTIVE_RANGE, MAX_RANGE)
                        .durability(DURABILITY)
                        .precision(PRECISION)
                        .randomWeight(0.3D)
                        .build(),
                moveset,
                SPICE_GIRL_ENTITY.get(),
                id)
                .standControlType(StandControlType.CLOSE_RANGE_DIRECT)
                .manualControl(true)
                .distanceStrengthDecay(true)
                .standDimensions(STAND_WIDTH, STAND_HEIGHT)
                .summonShout(() -> SG_SUMMON_VOICE)
                .summonSound(() -> SG_SUMMON)
                .unsummonSound(() -> SG_UNSUMMON)
                .ost(() -> SPICE_GIRL_OST);
    }
}
