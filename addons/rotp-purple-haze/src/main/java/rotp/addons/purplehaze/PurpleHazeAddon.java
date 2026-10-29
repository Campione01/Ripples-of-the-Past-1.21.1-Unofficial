package rotp.addons.purplehaze;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import javax.annotation.Nullable;

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
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.util.sound.OstSoundList;
import rotp.core.impl.stands._entitybase.StandEntityBlockAbility;

import rotp.addons.purplehaze.ability.PurpleHazeBarrageAbility;
import rotp.addons.purplehaze.ability.PurpleHazeCapsuleShotAbility;
import rotp.addons.purplehaze.ability.PurpleHazeHeavyPunchAbility;
import rotp.addons.purplehaze.ability.PurpleHazeMadnessAbility;
import rotp.addons.purplehaze.ability.PurpleHazeMadnessBarrageAbility;
import rotp.addons.purplehaze.ability.PurpleHazePunchAbility;
import rotp.addons.purplehaze.ability.PurpleHazeViralPunchAbility;
import rotp.addons.purplehaze.ability.PurpleHazeVirusAuraAbility;
import rotp.addons.purplehaze.effect.PurpleHazeVirusEffect;
import rotp.addons.purplehaze.entity.CapsuleProjectileEntity;
import rotp.addons.purplehaze.entity.PurpleHazeCloudEntity;
import rotp.addons.purplehaze.entity.PurpleHazeEntity;
import rotp.addons.purplehaze.particle.PurpleHazeVirusParticleType;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(PurpleHazeAddon.MOD_ID)
public final class PurpleHazeAddon {
    public static final String MOD_ID = "rotp_ph";

    public static final String PUNCH_NAME = "punch";
    public static final String BARRAGE_NAME = "barrage";
    public static final String HEAVY_PUNCH_NAME = "heavy_punch";
    public static final String VIRAL_PUNCH_NAME = "viral_punch";
    public static final String CAPSULE_SHOT_NAME = "capsule_shot";
    public static final String VIRUS_AURA_NAME = "virus_aura";
    public static final String MADNESS_NAME = "madness";
    public static final String MADNESS_BARRAGE_NAME =
            "madness_barrage";

    public static final float STAND_WIDTH = 0.7F;
    public static final float STAND_HEIGHT = 2.1F;
    public static final double POWER = 14.0D;
    public static final double SPEED = 12.0D;
    public static final double EFFECTIVE_RANGE = 2.0D;
    public static final double MAX_RANGE = 5.0D;
    public static final double DURABILITY = 4.0D;
    public static final double PRECISION = 4.0D;

    private static final int REQUIRED_CORE_ABI = 1;

    public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES =
            DeferredRegister.create(
                    JojoRegistries.ABILITY_TYPES_REG,
                    MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(
                    Registries.ENTITY_TYPE,
                    MOD_ID);
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(
                    Registries.MOB_EFFECT,
                    MOD_ID);
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(
                    Registries.PARTICLE_TYPE,
                    MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(
                    Registries.SOUND_EVENT,
                    MOD_ID);
    public static final DeferredRegister<StandType> STAND_TYPES =
            DeferredRegister.create(
                    JojoRegistries.DEFAULT_STANDS_REG,
                    MOD_ID);

    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<PurpleHazePunchAbility>>
            PURPLE_HAZE_PUNCH = ABILITY_TYPES.register(
                    "purple_haze_punch",
                    key -> new AbilityType<>(
                            key,
                            PurpleHazePunchAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<PurpleHazeBarrageAbility>>
            PURPLE_HAZE_BARRAGE = ABILITY_TYPES.register(
                    "purple_haze_barrage",
                    key -> new AbilityType<>(
                            key,
                            PurpleHazeBarrageAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<PurpleHazeMadnessBarrageAbility>>
            PURPLE_HAZE_MADNESS_BARRAGE =
                    ABILITY_TYPES.register(
                            "purple_haze_madness_barrage",
                            key -> new AbilityType<>(
                                    key,
                                    PurpleHazeMadnessBarrageAbility
                                            ::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<PurpleHazeViralPunchAbility>>
            PURPLE_HAZE_VIRAL_PUNCH = ABILITY_TYPES.register(
                    "purple_haze_viral_punch",
                    key -> new AbilityType<>(
                            key,
                            PurpleHazeViralPunchAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<PurpleHazeHeavyPunchAbility>>
            PURPLE_HAZE_HEAVY_PUNCH =
                    ABILITY_TYPES.register(
                            "purple_haze_heavy_punch",
                            key -> new AbilityType<>(
                                    key,
                                    PurpleHazeHeavyPunchAbility
                                            ::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<StandEntityBlockAbility>>
            PURPLE_HAZE_BLOCK = ABILITY_TYPES.register(
                    "purple_haze_block",
                    key -> new AbilityType<>(
                            key,
                            StandEntityBlockAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<PurpleHazeCapsuleShotAbility>>
            PURPLE_HAZE_CAPSULE_SHOT =
                    ABILITY_TYPES.register(
                            "purple_haze_capsule_shot",
                            key -> new AbilityType<>(
                                    key,
                                    PurpleHazeCapsuleShotAbility
                                            ::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<PurpleHazeVirusAuraAbility>>
            PURPLE_HAZE_VIRUS_AURA =
                    ABILITY_TYPES.register(
                            "purple_haze_virus_aura",
                            key -> new AbilityType<>(
                                    key,
                                    PurpleHazeVirusAuraAbility
                                            ::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<PurpleHazeMadnessAbility>>
            PURPLE_HAZE_MADNESS = ABILITY_TYPES.register(
                    "purple_haze_madness",
                    key -> new AbilityType<>(
                            key,
                            PurpleHazeMadnessAbility::new));

    public static final DeferredHolder<
            EntityType<?>,
            EntityType<PurpleHazeEntity>>
            PURPLE_HAZE_ENTITY = ENTITY_TYPES.register(
                    "purple_haze",
                    key -> EntityType.Builder
                            .<PurpleHazeEntity>of(
                                    PurpleHazeEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(STAND_WIDTH, STAND_HEIGHT)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));
    public static final DeferredHolder<
            EntityType<?>,
            EntityType<CapsuleProjectileEntity>>
            CAPSULE_PROJECTILE = ENTITY_TYPES.register(
                    "capsule_projectile",
                    key -> EntityType.Builder
                            .<CapsuleProjectileEntity>of(
                                    CapsuleProjectileEntity::new,
                                    MobCategory.MISC)
                            .noSummon()
                            .sized(0.75F, 0.75F)
                            .clientTrackingRange(32)
                            .updateInterval(10)
                            .build(key.toString()));
    public static final DeferredHolder<
            EntityType<?>,
            EntityType<PurpleHazeCloudEntity>>
            PURPLE_HAZE_CLOUD = ENTITY_TYPES.register(
                    "purple_haze_cloud",
                    key -> EntityType.Builder
                            .<PurpleHazeCloudEntity>of(
                                    PurpleHazeCloudEntity::new,
                                    MobCategory.MISC)
                            .noSummon()
                            .sized(0.1F, 0.1F)
                            .clientTrackingRange(48)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<
            MobEffect,
            PurpleHazeVirusEffect>
            PH_VIRUS = MOB_EFFECTS.register(
                    "ph_virus",
                    key -> new PurpleHazeVirusEffect());
    public static final DeferredHolder<
            ParticleType<?>,
            PurpleHazeVirusParticleType>
            PURPLE_HAZE_VIRUS_PARTICLE =
                    PARTICLE_TYPES.register(
                            "purple_haze_virus",
                            key -> new PurpleHazeVirusParticleType());

    public static final DeferredHolder<SoundEvent, SoundEvent>
            PH_SUMMON = sound("ph_summon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            PH_LIGHT_PUNCH = sound("ph_light_punch");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            PH_FUGO_LIGHT_PUNCH = sound("ph_fugo_light_punch");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            PH_HEAVY_PUNCH = sound("ph_heavy_punch");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            PH_FUGO_HEAVY_PUNCH =
            sound("ph_fugo_heavy_punch");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            PH_BARRAGE = sound("ph_barrage");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            PH_FUGO_BARRAGE = sound("ph_fugo_barrage");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            PH_FINISHER = sound("ph_finisher");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            PH_FUGO_FINISHER = sound("ph_fugo_finisher");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            PH_AURA = sound("ph_aura");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            PH_FUGO_AURA = sound("ph_fugo_aura");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            PH_MADNESS = sound("ph_madness");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            PH_FUGO_MADNESS = sound("ph_fugo_madness");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            PH_FUGO_CAPSULE_SHOT =
            sound("ph_fugo_capsule_shot");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            CAPSULE_SHOT = sound("capsule_shot");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            CAPSULE_EXPLOSION = sound("capsule_explosion");
    public static final OstSoundList PURPLE_HAZE_OST =
            new OstSoundList(id("purple_haze_ost"), SOUND_EVENTS);

    public static final DeferredHolder<StandType, EntityStandType>
            PURPLE_HAZE = STAND_TYPES.register(
                    "purple_haze",
                    PurpleHazeAddon::createStand);

    public PurpleHazeAddon(IEventBus modEventBus) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_VISUAL_CONTEXT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_SHOUTS_V1)) {
            throw new IllegalStateException(
                    "RotP Purple Haze requires ROTP addon ABI 1 with "
                            + "namespaced ability resources and Stand "
                            + "visual contexts.");
        }

        ABILITY_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        MOB_EFFECTS.register(modEventBus);
        PARTICLE_TYPES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                MOD_ID,
                path);
    }

    @Nullable
    public static PurpleHazeEntity controller(
            @Nullable LivingEntity owner) {
        if (owner == null) {
            return null;
        }
        StandPower power = StandPower.get(owner);
        return power != null
                && power.getPowerType() == PURPLE_HAZE.get()
                && power.getSummonedStandEntity()
                        instanceof PurpleHazeEntity stand
                && stand.isAlive()
                ? stand
                : null;
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(
            String path) {
        return SOUND_EVENTS.register(
                path,
                SoundEvent::createVariableRangeEvent);
    }

    private static EntityStandType createStand(
            ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addHumanoidStandStuff()
                .addAbility(
                        PUNCH_NAME,
                        PURPLE_HAZE_PUNCH,
                        punch -> punch
                                .userShoutSound(
                                        PH_FUGO_LIGHT_PUNCH)
                                .standCrySound(
                                        PH_LIGHT_PUNCH))
                // 1.16 barrage had no .shout(): Fugo stays silent. Its
                // one-argument standSound also cried with the arms only.
                .addAbility(
                        BARRAGE_NAME,
                        PURPLE_HAZE_BARRAGE,
                        barrage -> barrage.barrageCrySound(
                                PH_BARRAGE)
                                .barrageCryInArmsOnly())
                .addAbility(
                        HEAVY_PUNCH_NAME,
                        PURPLE_HAZE_HEAVY_PUNCH,
                        punch -> punch
                                .userShoutSound(
                                        PH_FUGO_HEAVY_PUNCH)
                                .heavyPunchCrySound(
                                        PH_HEAVY_PUNCH))
                .addAbility(
                        "heavy_charged",
                        ModStandAbilities.HEAVY_CHARGED)
                .addAbility(
                        VIRAL_PUNCH_NAME,
                        PURPLE_HAZE_VIRAL_PUNCH,
                        punch -> {
                            punch.userShoutSound(
                                    PH_FUGO_FINISHER);
                            punch.heavyPunchCrySound(
                                    PH_FINISHER);
                            punch.initIsFinisher(
                                    HEAVY_PUNCH_NAME);
                        })
                .addAbility(
                        MADNESS_BARRAGE_NAME,
                        PURPLE_HAZE_MADNESS_BARRAGE,
                        // 1.16 one-argument standSound: cried with the arms only too
                        barrage -> barrage
                                .barrageCrySound(PH_BARRAGE)
                                .barrageCryInArmsOnly())
                .addAbility("grab", ModStandAbilities.GRAB)
                .addAbility(
                        "grab_throw",
                        ModStandAbilities.GRAB_THROW)
                .addAbility(
                        "grab_punch",
                        PURPLE_HAZE_PUNCH,
                        punch -> {
                            punch.userShoutSound(
                                    PH_FUGO_LIGHT_PUNCH);
                            punch.standCrySound(
                                    PH_LIGHT_PUNCH);
                            punch.initIsGrabVariation();
                        })
                .addAbility(
                        "grab_barrage",
                        PURPLE_HAZE_BARRAGE,
                        barrage -> {
                            barrage.barrageCrySound(
                                    PH_BARRAGE);
                            barrage.initIsGrabVariation();
                        })
                .addAbility(
                        "grab_heavy_punch",
                        PURPLE_HAZE_HEAVY_PUNCH,
                        punch -> {
                            punch.userShoutSound(
                                    PH_FUGO_HEAVY_PUNCH);
                            punch.heavyPunchCrySound(
                                    PH_HEAVY_PUNCH);
                            punch.initIsGrabVariation();
                        })
                .addAbility("guard", PURPLE_HAZE_BLOCK)
                .addAbility(
                        CAPSULE_SHOT_NAME,
                        PURPLE_HAZE_CAPSULE_SHOT,
                        ability -> {
                            ability.userShoutSound(
                                    PH_FUGO_CAPSULE_SHOT);
                            ability.cooldown(20);
                            ability.resolveLevelToUnlock(1);
                        })
                .addAbility(
                        VIRUS_AURA_NAME,
                        PURPLE_HAZE_VIRUS_AURA,
                        ability -> {
                            ability.userShoutSound(
                                    PH_FUGO_AURA);
                            ability.cooldown(20);
                            ability.resolveLevelToUnlock(2);
                        })
                .addAbility(
                        MADNESS_NAME,
                        PURPLE_HAZE_MADNESS,
                        ability -> {
                            ability.cooldown(400);
                            ability.resolveLevelToUnlock(3);
                        })
                .makeControlScheme("hotbar")
                    .bind(
                            PUNCH_NAME,
                            InputMethod.CLICK,
                            InputKey.LMB)
                    .bind(
                            "barrage",
                            InputMethod.HOLD,
                            InputKey.LMB)
                    .bind(
                            HEAVY_PUNCH_NAME,
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
                            0,
                            USE_SPECIAL,
                            SWITCH_SPECIAL)
                    .addToHotbar(
                            CAPSULE_SHOT_NAME,
                            0,
                            InputMethod.HOLD)
                    .addToHotbar(
                            VIRUS_AURA_NAME,
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            MADNESS_NAME,
                            0,
                            InputMethod.HOLD)
                .finalizeControlScheme()
                .makeControlScheme("keybinds")
                    .bind(
                            PUNCH_NAME,
                            InputMethod.CLICK,
                            InputKey.LMB)
                    .bind(
                            "barrage",
                            InputMethod.HOLD,
                            InputKey.LMB)
                    .bind(
                            HEAVY_PUNCH_NAME,
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
                        StandUnlockableSkill.startingAbility(
                                PUNCH_NAME))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                BARRAGE_NAME))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                HEAVY_PUNCH_NAME))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                        "heavy_charged")
                                .prerequisiteSkill(
                                        HEAVY_PUNCH_NAME))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                VIRAL_PUNCH_NAME))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                "guard"))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                "grab"))
                .addSkill(
                        StandUnlockableSkill.unlockableAbility(
                                        "grab_throw",
                                        100)
                                .withAbility(
                                        "grab_punch",
                                        "grab_barrage",
                                        "grab_heavy_punch")
                                .prerequisiteSkill("grab"))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                CAPSULE_SHOT_NAME))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                VIRUS_AURA_NAME))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                        MADNESS_NAME)
                                .withAbility(
                                        MADNESS_BARRAGE_NAME))
                .addHumanoidStandSkills();

        return new EntityStandType(
                new StandStats.Builder()
                        .power(POWER)
                        .speed(SPEED)
                        .range(
                                EFFECTIVE_RANGE,
                                MAX_RANGE)
                        .durability(DURABILITY)
                        .precision(PRECISION)
                        .build(),
                moveset,
                PURPLE_HAZE_ENTITY.get(),
                id)
                .standControlType(StandControlType.CLOSE_RANGE_DIRECT)
                .manualControl(true)
                .distanceStrengthDecay(true)
                .standDimensions(
                        STAND_WIDTH,
                        STAND_HEIGHT)
                .summonShout(() -> PH_SUMMON)
                .summonSound(
                        () -> ModSoundEvents.STAND_SUMMON)
                .unsummonSound(
                        () -> ModSoundEvents.STAND_UNSUMMON)
                .ost(() -> PURPLE_HAZE_OST);
    }
}
