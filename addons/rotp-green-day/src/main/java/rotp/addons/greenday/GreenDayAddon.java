package rotp.addons.greenday;

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
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.impl.stands._entitybase.StandEntityBlockAbility;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;

import rotp.addons.greenday.ability.GreenDayHealAbility;
import rotp.addons.greenday.ability.GreenDayMoldAbility;
import rotp.addons.greenday.ability.GreenDayShootArmAbility;
import rotp.addons.greenday.ability.GreenDaySpikeAbility;
import rotp.addons.greenday.effect.GreenDayArmlessEffect;
import rotp.addons.greenday.effect.GreenDayMarkerEffect;
import rotp.addons.greenday.effect.GreenDayMoldEffect;
import rotp.addons.greenday.entity.GreenDayStandEntity;
import rotp.addons.greenday.entity.ShootingArmEntity;
import rotp.addons.greenday.state.GreenDayMoldState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@Mod(GreenDayAddon.MOD_ID)
public final class GreenDayAddon {
    public static final String MOD_ID = "rotp_zgd";
    public static final int REQUIRED_CORE_ABI = 1;

    public static final float STAND_WIDTH = 0.65F;
    public static final float STAND_HEIGHT = 1.95F;
    public static final double POWER = 15.7D;
    public static final double SPEED = 10.0D;
    public static final double EFFECTIVE_RANGE = 2.0D;
    public static final double MAX_RANGE = 128.0D;
    public static final double DURABILITY = 15.0D;
    public static final double PRECISION = 4.0D;

    public static final String LEGACY_GD_PUNCH = "gd_punch";
    public static final String LEGACY_GD_BARRAGE = "gd_barrage";
    public static final String LEGACY_GD_COMBO_PUNCH =
            "gd_combo_punch";
    public static final String LEGACY_GD_HEAVY_PUNCH =
            "gd_heavy_punch";
    public static final String LEGACY_GD_BLOCK = "gd_block";
    public static final String LEGACY_GD_MOLD = "gd_mold";
    public static final String LEGACY_GD_HEAL = "gd_heal";
    public static final String LEGACY_GD_SHOT_LEFT_ARM =
            "gd_shot_left_arm";
    public static final String LEGACY_GD_SHOT_RIGHT_ARM =
            "gd_shot_right_arm";

    private static final String PUNCH = "punch";
    private static final String BARRAGE = "barrage";
    private static final String COMBO_PUNCH = "finisher_punch";
    private static final String HEAVY_PUNCH = "heavy_punch";
    private static final String GUARD = "guard";
    private static final String MOLD_ABILITY = "mold";
    private static final String HEAL_ABILITY = "heal";
    private static final String SHOT_LEFT_ARM = "shot_left_arm";
    private static final String SHOT_RIGHT_ARM = "shot_right_arm";

    public static final DeferredRegister<AbilityType<?>>
            ABILITY_TYPES = DeferredRegister.create(
                    JojoRegistries.ABILITY_TYPES_REG,
                    MOD_ID);
    public static final DeferredRegister<AttachmentType<?>>
            ATTACHMENT_TYPES = DeferredRegister.create(
                    NeoForgeRegistries.ATTACHMENT_TYPES,
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
            AbilityType<?>, AbilityType<StandEntityPunchAbility>>
            GREEN_DAY_PUNCH = ABILITY_TYPES.register(
                    LEGACY_GD_PUNCH,
                    key -> new AbilityType<>(
                            key, StandEntityPunchAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<StandEntityBarrageAbility>>
            GREEN_DAY_BARRAGE = ABILITY_TYPES.register(
                    LEGACY_GD_BARRAGE,
                    key -> new AbilityType<>(
                            key, StandEntityBarrageAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<GreenDaySpikeAbility>>
            GREEN_DAY_COMBO_PUNCH = ABILITY_TYPES.register(
                    LEGACY_GD_COMBO_PUNCH,
                    key -> new AbilityType<>(
                            key, GreenDaySpikeAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<StandEntityHeavyPunchAbility>>
            GREEN_DAY_HEAVY_PUNCH = ABILITY_TYPES.register(
                    LEGACY_GD_HEAVY_PUNCH,
                    key -> new AbilityType<>(
                            key, StandEntityHeavyPunchAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<StandEntityBlockAbility>>
            GREEN_DAY_BLOCK = ABILITY_TYPES.register(
                    LEGACY_GD_BLOCK,
                    key -> new AbilityType<>(
                            key, StandEntityBlockAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<GreenDayMoldAbility>>
            GREEN_DAY_MOLD = ABILITY_TYPES.register(
                    LEGACY_GD_MOLD,
                    key -> new AbilityType<>(
                            key, GreenDayMoldAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<GreenDayHealAbility>>
            GREEN_DAY_HEAL = ABILITY_TYPES.register(
                    LEGACY_GD_HEAL,
                    key -> new AbilityType<>(
                            key, GreenDayHealAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<GreenDayShootArmAbility>>
            GREEN_DAY_SHOT_LEFT_ARM = ABILITY_TYPES.register(
                    LEGACY_GD_SHOT_LEFT_ARM,
                    key -> new AbilityType<>(
                            key,
                            (type, id) -> new GreenDayShootArmAbility(
                                    type, id, false)));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<GreenDayShootArmAbility>>
            GREEN_DAY_SHOT_RIGHT_ARM = ABILITY_TYPES.register(
                    LEGACY_GD_SHOT_RIGHT_ARM,
                    key -> new AbilityType<>(
                            key,
                            (type, id) -> new GreenDayShootArmAbility(
                                    type, id, true)));

    public static final Supplier<AttachmentType<GreenDayMoldState>>
            MOLD_STATE = ATTACHMENT_TYPES.register(
                    "mold_state",
                    () -> AttachmentType.serializable(
                                    holder ->
                                            holder instanceof LivingEntity
                                                    ? new GreenDayMoldState()
                                                    : null)
                            .sync(GreenDayMoldState.STREAM_CODEC)
                            .build());

    public static final DeferredHolder<
            EntityType<?>, EntityType<GreenDayStandEntity>>
            GREEN_DAY_ENTITY = ENTITY_TYPES.register(
                    "green_day",
                    key -> EntityType.Builder
                            .<GreenDayStandEntity>of(
                                    GreenDayStandEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(STAND_WIDTH, STAND_HEIGHT)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));
    public static final DeferredHolder<
            EntityType<?>, EntityType<ShootingArmEntity>>
            SHOOTING_ARM = ENTITY_TYPES.register(
                    "shooting_arm",
                    key -> EntityType.Builder
                            .<ShootingArmEntity>of(
                                    ShootingArmEntity::new,
                                    MobCategory.MISC)
                            .fireImmune()
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(64)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<
            MobEffect, GreenDayMoldEffect> MOLD =
            MOB_EFFECTS.register(
                    "mold",
                    key -> new GreenDayMoldEffect());
    public static final DeferredHolder<
            MobEffect, GreenDayMarkerEffect> MOLD_UTIL =
            MOB_EFFECTS.register(
                    "mold_util",
                    key -> new GreenDayMarkerEffect(
                            MobEffectCategory.HARMFUL,
                            GreenDayMoldEffect.COLOR));
    public static final DeferredHolder<
            MobEffect, GreenDayArmlessEffect> LEFT_ARMLESS =
            MOB_EFFECTS.register(
                    "left_armless",
                    key -> new GreenDayArmlessEffect(false));
    public static final DeferredHolder<
            MobEffect, GreenDayArmlessEffect> RIGHT_ARMLESS =
            MOB_EFFECTS.register(
                    "right_armless",
                    key -> new GreenDayArmlessEffect(true));

    public static final DeferredHolder<SoundEvent, SoundEvent>
            GD_SUMMON = sound("gd_summon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            GD_KICK = sound("gd_kick");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            GD_MOLD = sound("gd_mold");

    public static final DeferredHolder<StandType, EntityStandType>
            GREEN_DAY = STAND_TYPES.register(
                    "green_day", GreenDayAddon::createStand);

    public GreenDayAddon(IEventBus modEventBus) {
        requireCoreApi();
        ABILITY_TYPES.register(modEventBus);
        ATTACHMENT_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        MOB_EFFECTS.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                MOD_ID, path);
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
                                .FEATURE_FIRST_PERSON_POST_ARM_LAYERS_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_SCOPED_PLAYER_MODEL_VISIBILITY_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_POWER_ACTION_SLOT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_ACTION_PORT_SLOT_V1)) {
            throw new IllegalStateException(
                    "RotP Green Day requires ROTP addon ABI 1 with "
                            + "namespaced ability resources and "
                            + "stand visual contexts plus first-person "
                            + "post-arm layers, scoped player-model "
                            + "visibility and the Stand-power action "
                            + "slot for its arm shots.");
        }
    }

    private static EntityStandType createStand(
            ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addHumanoidStandStuff()
                .addAbility(
                        PUNCH,
                        GREEN_DAY_PUNCH)
                .addAbility(
                        BARRAGE,
                        GREEN_DAY_BARRAGE,
                        barrage -> barrage
                                .barrageHitSound(
                                        ModSoundEvents
                                                .STAND_PUNCH_LIGHT))
                .addAbility(
                        HEAVY_PUNCH,
                        GREEN_DAY_HEAVY_PUNCH,
                        punch -> punch
                                .heavyPunchImpactSound(GD_KICK))
                .addAbility(
                        "heavy_charged",
                        ModStandAbilities.HEAVY_CHARGED)
                .addAbility(
                        COMBO_PUNCH,
                        GREEN_DAY_COMBO_PUNCH,
                        punch -> {
                            punch.heavyPunchImpactSound(GD_KICK);
                            punch.initIsFinisher(HEAVY_PUNCH);
                            punch.resolveLevelToUnlock(1);
                        })
                .addAbility("grab", ModStandAbilities.GRAB)
                .addAbility(
                        "grab_throw",
                        ModStandAbilities.GRAB_THROW)
                .addAbility(
                        "grab_punch",
                        GREEN_DAY_PUNCH,
                        punch -> {
                            punch.initIsGrabVariation();
                        })
                .addAbility(
                        "grab_barrage",
                        GREEN_DAY_BARRAGE,
                        barrage -> {
                            barrage.barrageHitSound(
                                    ModSoundEvents
                                            .STAND_PUNCH_LIGHT);
                            barrage.initIsGrabVariation();
                        })
                .addAbility(
                        "grab_heavy_punch",
                        GREEN_DAY_HEAVY_PUNCH,
                        punch -> {
                            punch.heavyPunchImpactSound(GD_KICK);
                            punch.initIsGrabVariation();
                        })
                .addAbility(GUARD, GREEN_DAY_BLOCK)
                .addAbility(MOLD_ABILITY, GREEN_DAY_MOLD)
                .addAbility(HEAL_ABILITY, GREEN_DAY_HEAL)
                .addAbility(
                        SHOT_LEFT_ARM,
                        GREEN_DAY_SHOT_LEFT_ARM)
                .addAbility(
                        SHOT_RIGHT_ARM,
                        GREEN_DAY_SHOT_RIGHT_ARM)
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
                    .bind(
                            GUARD,
                            InputMethod.HOLD,
                            InputKey.RMB)
                    .makeHotbar(
                            0, USE_SPECIAL, SWITCH_SPECIAL)
                    .addToHotbar(
                            MOLD_ABILITY,
                            0,
                            InputMethod.HOLD)
                    .addToHotbar(
                            HEAL_ABILITY,
                            0,
                            InputMethod.HOLD)
                    .addToHotbar(
                            SHOT_LEFT_ARM,
                            0,
                            InputMethod.CLICK)
                    .addHotbarSlotVariation(
                            SHOT_RIGHT_ARM,
                            SHOT_LEFT_ARM,
                            InputKey.Modifier.SHIFT,
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
                            MovesetBuilder
                                    .DEFAULT_GRAB_THROW_INPUT)
                    .bind(
                            GUARD,
                            InputMethod.HOLD,
                            InputKey.RMB)
                .finalizeControlScheme()
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                PUNCH))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                BARRAGE))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                HEAVY_PUNCH))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                        "heavy_charged")
                                .prerequisiteSkill(
                                        HEAVY_PUNCH))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                COMBO_PUNCH))
                .addSkill(
                        StandUnlockableSkill.startingAbility(
                                GUARD))
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
                        StandUnlockableSkill.unlockableAbility(
                                MOLD_ABILITY,
                                200))
                .addSkill(
                        StandUnlockableSkill.unlockableAbility(
                                        SHOT_LEFT_ARM,
                                        300)
                                .withAbility(SHOT_RIGHT_ARM)
                                .prerequisiteSkill(
                                        MOLD_ABILITY))
                .addSkill(
                        StandUnlockableSkill.unlockableAbility(
                                        HEAL_ABILITY,
                                        400)
                                .prerequisiteSkill(
                                        MOLD_ABILITY))
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
                GREEN_DAY_ENTITY.get(),
                id)
                .standControlType(StandControlType.CLOSE_RANGE_DIRECT)
                .manualControl(true)
                .distanceStrengthDecay(true)
                .standDimensions(
                        STAND_WIDTH, STAND_HEIGHT)
                .summonSound(() -> GD_SUMMON)
                .unsummonSound(
                        () -> ModSoundEvents.STAND_UNSUMMON);
    }
}
