package rotp.addons.littlefeet;

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
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.util.sound.OstSoundList;

import rotp.addons.littlefeet.ability.LittleFeetFinisherAbility;
import rotp.addons.littlefeet.ability.LittleFeetGrowTargetsAbility;
import rotp.addons.littlefeet.ability.LittleFeetHeavyPunchAbility;
import rotp.addons.littlefeet.ability.LittleFeetInstantSelfScaleAbility;
import rotp.addons.littlefeet.ability.LittleFeetShrinkSlashAbility;
import rotp.addons.littlefeet.ability.LittleFeetSlowSelfScaleAbility;
import rotp.addons.littlefeet.entity.LittleFeetEntity;
import rotp.addons.littlefeet.scale.LittleFeetScaleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(LittleFeetAddon.MOD_ID)
public final class LittleFeetAddon {
    public static final String MOD_ID = "rotp_littlefeet";
    public static final String SHRINK_SLASH_NAME =
            "little_feet_shrink_slash";
    public static final String FINISHER_NAME =
            "little_feet_finisher_punch";
    public static final String INSTANT_SELF_SHRINK_NAME =
            "little_feet_instant_self_shrink";
    public static final String INSTANT_SELF_GROW_NAME =
            "little_feet_instant_self_grow";
    public static final String SLOW_SELF_SHRINK_NAME =
            "little_feet_slow_self_shrink";
    public static final String SLOW_SELF_GROW_NAME =
            "little_feet_slow_self_grow";
    public static final String NEAREST_TARGET_GROW_NAME =
            "little_feet_nearest_entity_grow";
    public static final String ALL_TARGETS_GROW_NAME =
            "little_feet_every_entity_grow";

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
            AbilityType<?>,
            AbilityType<LittleFeetShrinkSlashAbility>>
            SHRINK_SLASH = ABILITY_TYPES.register(
                    SHRINK_SLASH_NAME,
                    key -> new AbilityType<>(
                            key, LittleFeetShrinkSlashAbility::new));
    // 1.16 heavy punch shouted FORMAGGIO_BASTARD from the user
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<LittleFeetHeavyPunchAbility>>
            HEAVY_PUNCH = ABILITY_TYPES.register(
                    "heavy_punch",
                    key -> new AbilityType<>(
                            key, LittleFeetHeavyPunchAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<LittleFeetFinisherAbility>>
            FINISHER = ABILITY_TYPES.register(
                    FINISHER_NAME,
                    key -> new AbilityType<>(
                            key, LittleFeetFinisherAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<LittleFeetInstantSelfScaleAbility>>
            INSTANT_SELF_SHRINK = ABILITY_TYPES.register(
                    INSTANT_SELF_SHRINK_NAME,
                    key -> new AbilityType<>(
                            key,
                            (type, id) ->
                                    new LittleFeetInstantSelfScaleAbility(
                                            type, id, true)));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<LittleFeetInstantSelfScaleAbility>>
            INSTANT_SELF_GROW = ABILITY_TYPES.register(
                    INSTANT_SELF_GROW_NAME,
                    key -> new AbilityType<>(
                            key,
                            (type, id) ->
                                    new LittleFeetInstantSelfScaleAbility(
                                            type, id, false)));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<LittleFeetSlowSelfScaleAbility>>
            SLOW_SELF_SHRINK = ABILITY_TYPES.register(
                    SLOW_SELF_SHRINK_NAME,
                    key -> new AbilityType<>(
                            key,
                            (type, id) ->
                                    new LittleFeetSlowSelfScaleAbility(
                                            type, id, true)));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<LittleFeetSlowSelfScaleAbility>>
            SLOW_SELF_GROW = ABILITY_TYPES.register(
                    SLOW_SELF_GROW_NAME,
                    key -> new AbilityType<>(
                            key,
                            (type, id) ->
                                    new LittleFeetSlowSelfScaleAbility(
                                            type, id, false)));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<LittleFeetGrowTargetsAbility>>
            NEAREST_TARGET_GROW = ABILITY_TYPES.register(
                    NEAREST_TARGET_GROW_NAME,
                    key -> new AbilityType<>(
                            key,
                            (type, id) ->
                                    new LittleFeetGrowTargetsAbility(
                                            type, id, false)));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<LittleFeetGrowTargetsAbility>>
            ALL_TARGETS_GROW = ABILITY_TYPES.register(
                    ALL_TARGETS_GROW_NAME,
                    key -> new AbilityType<>(
                            key,
                            (type, id) ->
                                    new LittleFeetGrowTargetsAbility(
                                            type, id, true)));

    public static final DeferredHolder<
            EntityType<?>,
            EntityType<LittleFeetEntity>>
            LITTLE_FEET_ENTITY = ENTITY_TYPES.register(
                    "little_feet",
                    key -> EntityType.Builder.<LittleFeetEntity>of(
                                    LittleFeetEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.55F, 1.85F)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<SoundEvent, SoundEvent>
            FORMAGGIO_LITTLE_FEET =
                    sound("formaggio_little_feet");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            FORMAGGIO_BARRAGE =
                    sound("formaggio_barrage");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            FORMAGGIO_BASTARD =
                    sound("formaggio_bastard");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            FORMAGGIO_MAD =
                    sound("formaggio_mad");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            LITTLE_FEET_SUMMON =
                    sound("little_feet_summon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            LITTLE_FEET_SHRINK2 =
                    sound("little_feet_shrink2");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            LITTLE_FEET_INSTANT_SELF_SHRINK =
                    sound("little_feet_instant_self_shrink");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            LITTLE_FEET_INSTANT_SELF_GROW =
                    sound("little_feet_instant_self_grow");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            LITTLE_FEET_SLOW_SELF_SHRINK =
                    sound("little_feet_slow_self_shrink");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            LITTLE_FEET_SLOW_SELF_GROW =
                    sound("little_feet_slow_self_grow");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            LITTLE_FEET_SHRINK_SLASH =
                    sound("little_feet_shrink_slash");
    public static final OstSoundList LITTLE_FEET_OST =
            new OstSoundList(id("little_feet_ost"), SOUND_EVENTS);

    public static final DeferredHolder<StandType, EntityStandType>
            LITTLE_FEET =
                    STAND_TYPES.register(
                            "little_feet", LittleFeetAddon::createStand);

    public LittleFeetAddon(IEventBus modEventBus) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_VISUAL_CONTEXT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_SHOUTS_V1)) {
            throw new IllegalStateException(
                    "RotP Little Feet requires ROTP addon ABI 1 with "
                            + "namespaced ability resources, stand "
                            + "visual contexts and ability shouts.");
        }

        LittleFeetScaleTypes.initialize();
        ABILITY_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        modEventBus.addListener(LittleFeetEvents::registerAttributes);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static boolean isLittleFeetUser(LivingEntity entity) {
        StandPower power = StandPower.get(entity);
        return power != null
                && power.getPowerType() == LITTLE_FEET.get();
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
                        ModStandAbilities.PUNCH,
                        punch -> punch.punchImpactSound(
                                ModSoundEvents.STAND_PUNCH_LIGHT))
                .addAbility(
                        "barrage",
                        ModStandAbilities.BARRAGE,
                        barrage -> barrage
                                .barrageHitSound(
                                        ModSoundEvents
                                                .STAND_PUNCH_LIGHT)
                                .barrageCrySound(
                                        FORMAGGIO_BARRAGE)
                                // 1.16 one-argument standSound: cried with
                                // the arms only too
                                .barrageCryInArmsOnly())
                .addAbility(
                        "heavy_punch",
                        HEAVY_PUNCH,
                        punch -> punch
                                .heavyPunchImpactSound(
                                        ModSoundEvents
                                                .STAND_PUNCH_HEAVY))
                .addAbility(
                        "heavy_charged",
                        ModStandAbilities.HEAVY_CHARGED)
                .addAbility(
                        FINISHER_NAME,
                        FINISHER,
                        punch -> {
                            punch.heavyPunchImpactSound(
                                    ModSoundEvents
                                            .STAND_PUNCH_HEAVY);
                            punch.initIsFinisher("heavy_punch");
                        })
                .addAbility("guard", ModStandAbilities.GUARD)
                .addAbility(
                        SHRINK_SLASH_NAME,
                        SHRINK_SLASH,
                        slash -> slash
                                .punchImpactSound(
                                        LITTLE_FEET_SHRINK_SLASH))
                .addAbility(
                        INSTANT_SELF_SHRINK_NAME,
                        INSTANT_SELF_SHRINK)
                .addAbility(
                        INSTANT_SELF_GROW_NAME,
                        INSTANT_SELF_GROW)
                .addAbility(
                        SLOW_SELF_SHRINK_NAME,
                        SLOW_SELF_SHRINK)
                .addAbility(
                        SLOW_SELF_GROW_NAME,
                        SLOW_SELF_GROW)
                .addAbility(
                        NEAREST_TARGET_GROW_NAME,
                        NEAREST_TARGET_GROW)
                .addAbility(
                        ALL_TARGETS_GROW_NAME,
                        ALL_TARGETS_GROW)
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
                    .bind("guard", InputMethod.HOLD, InputKey.RMB)
                    .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
                    .addToHotbar(
                            SHRINK_SLASH_NAME,
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            INSTANT_SELF_SHRINK_NAME,
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            INSTANT_SELF_GROW_NAME,
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            SLOW_SELF_SHRINK_NAME,
                            0,
                            InputMethod.HOLD)
                    .addToHotbar(
                            SLOW_SELF_GROW_NAME,
                            0,
                            InputMethod.HOLD)
                    .addToHotbar(
                            NEAREST_TARGET_GROW_NAME,
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            ALL_TARGETS_GROW_NAME,
                            0,
                            InputMethod.CLICK)
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
                    .bind("guard", InputMethod.HOLD, InputKey.RMB)
                .finalizeControlScheme()
                .addSkill(StandUnlockableSkill.startingAbility("punch"))
                .addSkill(StandUnlockableSkill.startingAbility("barrage"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "heavy_punch"))
                .addSkill(StandUnlockableSkill.startingAbility(
                                "heavy_charged")
                        .prerequisiteSkill("heavy_punch"))
                .addSkill(StandUnlockableSkill.startingAbility("guard"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        SHRINK_SLASH_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        INSTANT_SELF_SHRINK_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        INSTANT_SELF_GROW_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        SLOW_SELF_SHRINK_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        SLOW_SELF_GROW_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        NEAREST_TARGET_GROW_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        ALL_TARGETS_GROW_NAME))
                .addHumanoidStandSkills();

        return new EntityStandType(
                new StandStats.Builder()
                        .power(6.0)
                        .speed(12.0, 12.0)
                        .range(3.0, 4.0)
                        .durability(15.0)
                        .precision(6.0, 6.0)
                        .build(),
                moveset,
                LITTLE_FEET_ENTITY.get(),
                id)
                .standControlType(StandControlType.CLOSE_RANGE_DIRECT)
                .manualControl(true)
                .distanceStrengthDecay(true)
                .standDimensions(0.55F, 1.85F)
                .summonShout(() -> FORMAGGIO_LITTLE_FEET)
                .summonSound(() -> LITTLE_FEET_SUMMON)
                .unsummonSound(() -> ModSoundEvents.STAND_UNSUMMON)
                .ost(() -> LITTLE_FEET_OST);
    }
}
