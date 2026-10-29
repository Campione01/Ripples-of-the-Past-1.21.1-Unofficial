package rotp.addons.cream;

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
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.util.sound.OstSoundList;
import rotp.core.impl.stands._entitybase.StandEntityBlockAbility;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;

import rotp.addons.cream.ability.CreamChompAbility;
import rotp.addons.cream.ability.CreamEatAbility;
import rotp.addons.cream.ability.CreamFinisherPunchAbility;
import rotp.addons.cream.ability.CreamMeleeAbility;
import rotp.addons.cream.ability.CreamMeleeAbility.Style;
import rotp.addons.cream.ability.CreamVoidAbility;
import rotp.addons.cream.ability.CreamVoidAbility.Kind;
import rotp.addons.cream.block.VoidBarrierBlock;
import rotp.addons.cream.effect.CreamBleedingEffect;
import rotp.addons.cream.effect.CreamMarkerEffect;
import rotp.addons.cream.effect.CreamWeakNeckEffect;
import rotp.addons.cream.entity.CreamEntity;
import rotp.addons.cream.stand.CreamStandType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(CreamAddon.MOD_ID)
public final class CreamAddon {
    public static final String MOD_ID = "rotp_cream";
    public static final int REQUIRED_CORE_ABI = 1;

    public static final String CREAM_PUNCH_NAME = "cream_punch";
    public static final String CREAM_CLAW_NAME = "cream_claw";
    public static final String CREAM_FINISHER_PUNCH_NAME =
            "cream_finisher_punch";
    public static final String CREAM_DOUBLE_CLAW_FINISHER_NAME =
            "cream_double_claw_finisher_punch";
    public static final String CREAM_HEAVY_CLAW_NAME =
            "cream_heavy_claw";
    public static final String CREAM_HEAVY_PUNCH_NAME =
            "cream_heavy_punch";
    public static final String CREAM_CHOP_NAME = "cream_chop";
    public static final String CREAM_CHOMP_NAME = "cream_chomp";
    public static final String CREAM_BLOCK_NAME = "cream_block";
    public static final String CREAM_VOID_BALL_NAME =
            "cream_void_ball";
    public static final String CREAM_VOID_BALL_CANCEL_NAME =
            "cream_void_ball_cancel";
    public static final String CREAM_VOID_DASH_NAME =
            "cream_void_dash";
    public static final String CREAM_EAT_ITEM_NAME =
            "cream_eat_item";
    public static final String CREAM_EAT_ITEM_STACK_NAME =
            "cream_eat_item_stack";
    public static final String CREAM_SEMI_VOID_NAME =
            "cream_semi_void_state";
    public static final String CREAM_SEMI_VOID_CANCEL_NAME =
            "cream_semi_void_state_cancel";

    public static final DeferredRegister<AbilityType<?>>
            ABILITY_TYPES = DeferredRegister.create(
                    JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, MOD_ID);
    public static final DeferredRegister<EntityType<?>>
            ENTITY_TYPES = DeferredRegister.create(
                    Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<StandType> STAND_TYPES =
            DeferredRegister.create(
                    JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);

    private static final AbilityType<CreamMeleeAbility>
            CREAM_PUNCH_TYPE = meleeAbilityType(
                    CREAM_PUNCH_NAME, Style.PUNCH);
    private static final AbilityType<CreamMeleeAbility>
            CREAM_CLAW_TYPE = meleeAbilityType(
                    CREAM_CLAW_NAME, Style.CLAW);
    private static final AbilityType<StandEntityHeavyPunchAbility>
            CREAM_FINISHER_PUNCH_TYPE = new AbilityType<>(
                    id(CREAM_FINISHER_PUNCH_NAME),
                    CreamFinisherPunchAbility::new);
    private static final AbilityType<StandEntityHeavyPunchAbility>
            CREAM_DOUBLE_CLAW_FINISHER_TYPE =
                    heavyPunchAbilityType(
                            CREAM_DOUBLE_CLAW_FINISHER_NAME);
    private static final AbilityType<StandEntityHeavyPunchAbility>
            CREAM_HEAVY_CLAW_TYPE =
                    heavyPunchAbilityType(
                            CREAM_HEAVY_CLAW_NAME);
    private static final AbilityType<StandEntityHeavyPunchAbility>
            CREAM_HEAVY_PUNCH_TYPE =
                    heavyPunchAbilityType(
                            CREAM_HEAVY_PUNCH_NAME);
    private static final AbilityType<CreamMeleeAbility>
            CREAM_CHOP_TYPE = meleeAbilityType(
                    CREAM_CHOP_NAME, Style.CHOP);
    private static final AbilityType<CreamChompAbility>
            CREAM_CHOMP_TYPE = new AbilityType<>(
                    id(CREAM_CHOMP_NAME),
                    CreamChompAbility::new);
    private static final AbilityType<StandEntityBlockAbility>
            CREAM_BLOCK_TYPE = new AbilityType<>(
                    id(CREAM_BLOCK_NAME),
                    StandEntityBlockAbility::new);
    private static final AbilityType<CreamVoidAbility>
            CREAM_VOID_BALL_TYPE = voidAbilityType(
                    CREAM_VOID_BALL_NAME, Kind.BALL);
    private static final AbilityType<CreamVoidAbility>
            CREAM_VOID_BALL_CANCEL_TYPE = voidAbilityType(
                    CREAM_VOID_BALL_CANCEL_NAME,
                    Kind.CANCEL_BALL);
    private static final AbilityType<CreamVoidAbility>
            CREAM_VOID_DASH_TYPE = voidAbilityType(
                    CREAM_VOID_DASH_NAME, Kind.DASH);
    private static final AbilityType<CreamEatAbility>
            CREAM_EAT_ITEM_TYPE = eatAbilityType(
                    CREAM_EAT_ITEM_NAME, false);
    private static final AbilityType<CreamEatAbility>
            CREAM_EAT_ITEM_STACK_TYPE = eatAbilityType(
                    CREAM_EAT_ITEM_STACK_NAME, true);
    private static final AbilityType<CreamVoidAbility>
            CREAM_SEMI_VOID_TYPE = voidAbilityType(
                    CREAM_SEMI_VOID_NAME, Kind.SEMI);
    private static final AbilityType<CreamVoidAbility>
            CREAM_SEMI_VOID_CANCEL_TYPE = voidAbilityType(
                    CREAM_SEMI_VOID_CANCEL_NAME,
                    Kind.CANCEL_SEMI);

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<CreamMeleeAbility>>
            CREAM_PUNCH = ABILITY_TYPES.register(
                    CREAM_PUNCH_NAME,
                    () -> CREAM_PUNCH_TYPE);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<CreamMeleeAbility>>
            CREAM_CLAW = ABILITY_TYPES.register(
                    CREAM_CLAW_NAME,
                    () -> CREAM_CLAW_TYPE);
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<StandEntityHeavyPunchAbility>>
            CREAM_FINISHER_PUNCH = ABILITY_TYPES.register(
                    CREAM_FINISHER_PUNCH_NAME,
                    () -> CREAM_FINISHER_PUNCH_TYPE);
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<StandEntityHeavyPunchAbility>>
            CREAM_DOUBLE_CLAW_FINISHER =
                    ABILITY_TYPES.register(
                            CREAM_DOUBLE_CLAW_FINISHER_NAME,
                            () -> CREAM_DOUBLE_CLAW_FINISHER_TYPE);
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<StandEntityHeavyPunchAbility>>
            CREAM_HEAVY_CLAW = ABILITY_TYPES.register(
                    CREAM_HEAVY_CLAW_NAME,
                    () -> CREAM_HEAVY_CLAW_TYPE);
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<StandEntityHeavyPunchAbility>>
            CREAM_HEAVY_PUNCH = ABILITY_TYPES.register(
                    CREAM_HEAVY_PUNCH_NAME,
                    () -> CREAM_HEAVY_PUNCH_TYPE);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<CreamMeleeAbility>>
            CREAM_CHOP = ABILITY_TYPES.register(
                    CREAM_CHOP_NAME,
                    () -> CREAM_CHOP_TYPE);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<CreamChompAbility>>
            CREAM_CHOMP = ABILITY_TYPES.register(
                    CREAM_CHOMP_NAME,
                    () -> CREAM_CHOMP_TYPE);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<StandEntityBlockAbility>>
            CREAM_BLOCK = ABILITY_TYPES.register(
                    CREAM_BLOCK_NAME,
                    () -> CREAM_BLOCK_TYPE);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<CreamVoidAbility>>
            CREAM_VOID_BALL = ABILITY_TYPES.register(
                    CREAM_VOID_BALL_NAME,
                    () -> CREAM_VOID_BALL_TYPE);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<CreamVoidAbility>>
            CREAM_VOID_BALL_CANCEL = ABILITY_TYPES.register(
                    CREAM_VOID_BALL_CANCEL_NAME,
                    () -> CREAM_VOID_BALL_CANCEL_TYPE);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<CreamVoidAbility>>
            CREAM_VOID_DASH = ABILITY_TYPES.register(
                    CREAM_VOID_DASH_NAME,
                    () -> CREAM_VOID_DASH_TYPE);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<CreamEatAbility>>
            CREAM_EAT_ITEM = ABILITY_TYPES.register(
                    CREAM_EAT_ITEM_NAME,
                    () -> CREAM_EAT_ITEM_TYPE);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<CreamEatAbility>>
            CREAM_EAT_ITEM_STACK = ABILITY_TYPES.register(
                    CREAM_EAT_ITEM_STACK_NAME,
                    () -> CREAM_EAT_ITEM_STACK_TYPE);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<CreamVoidAbility>>
            CREAM_SEMI_VOID = ABILITY_TYPES.register(
                    CREAM_SEMI_VOID_NAME,
                    () -> CREAM_SEMI_VOID_TYPE);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<CreamVoidAbility>>
            CREAM_SEMI_VOID_CANCEL = ABILITY_TYPES.register(
                    CREAM_SEMI_VOID_CANCEL_NAME,
                    () -> CREAM_SEMI_VOID_CANCEL_TYPE);

    public static final DeferredHolder<Block, VoidBarrierBlock>
            VOID_BARRIER = BLOCKS.register(
                    "void_barrier",
                    key -> new VoidBarrierBlock(
                            BlockBehaviour.Properties.ofFullCopy(
                                    Blocks.BARRIER)));

    public static final DeferredHolder<
            EntityType<?>, EntityType<CreamEntity>> CREAM_ENTITY =
            ENTITY_TYPES.register(
                    "cream",
                    key -> EntityType.Builder
                            .<CreamEntity>of(
                                    CreamEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(
                                    CreamSpec.ENTITY_WIDTH,
                                    CreamSpec.ENTITY_HEIGHT)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<
            MobEffect, CreamBleedingEffect> BLEEDING =
            MOB_EFFECTS.register(
                    "bleeding",
                    key -> new CreamBleedingEffect());
    public static final DeferredHolder<
            MobEffect, CreamMarkerEffect> TRUE_BLINDNESS =
            MOB_EFFECTS.register(
                    "true_blindness",
                    key -> new CreamMarkerEffect(
                            MobEffectCategory.HARMFUL,
                            CreamBleedingEffect.COLOR));
    public static final DeferredHolder<
            MobEffect, CreamWeakNeckEffect> WEAK_NECK =
            MOB_EFFECTS.register(
                    "weak_neck",
                    key -> new CreamWeakNeckEffect());
    public static final DeferredHolder<
            MobEffect, CreamMarkerEffect> VOIDED =
            MOB_EFFECTS.register(
                    "voided",
                    key -> new CreamMarkerEffect(
                            MobEffectCategory.NEUTRAL, 0));
    public static final DeferredHolder<
            MobEffect, CreamMarkerEffect> INSIDE_CREAM =
            MOB_EFFECTS.register(
                    "inside_cream",
                    key -> new CreamMarkerEffect(
                            MobEffectCategory.HARMFUL, 0));

    public static final DeferredHolder<SoundEvent, SoundEvent>
            VANILLA_ICE_CREAM = sound("vanilla_ice_cream");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            CREAM_SUMMON = sound("cream_summon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            CREAM_UNSUMMON = sound("cream_unsummon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            CREAM_CLAW_SOUND = sound("cream_claw");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            CREAM_ROAR = sound("cream_roar");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            CREAM_HEAVY_CLAW_SOUND = sound("cream_heavy_claw");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            CREAM_SKULLCRUSH = sound("cream_skullcrush");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            CREAM_ENTITY_VOID = sound("cream_entity_void");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            CREAM_VOID_FORM = sound("cream_void_form");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            CREAM_CHOMP_SOUND = sound("cream_chomp");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            CREAM_CHOMP_HIT = sound("cream_chomp_hit");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            CREAM_CHOMP_START = sound("cream_chomp_start");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            CREAM_VOID_FORM_SHORT = sound("cream_void_form_short");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            CREAM_VOID = sound("cream_void");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            CREAM_VOID_START = sound("cream_void_start");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            CREAM_VOID_END = sound("cream_void_end");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            CREAM_CHOP_SOUND = sound("cream_chop");
    public static final OstSoundList CREAM_OST =
            new OstSoundList(id("cream_ost"), SOUND_EVENTS);

    public static final DeferredHolder<
            StandType, CreamStandType> CREAM =
            STAND_TYPES.register("cream", CreamAddon::createStand);

    public CreamAddon(
            IEventBus modEventBus, ModContainer modContainer) {
        requireCoreApi();
        ABILITY_TYPES.register(modEventBus);
        BLOCKS.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        MOB_EFFECTS.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        modEventBus.addListener(
                CreamGameEvents::registerAttributes);
        modContainer.registerConfig(
                ModConfig.Type.COMMON, CreamConfig.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static AbilityType<CreamMeleeAbility>
            meleeAbilityType(String name, Style style) {
        return new AbilityType<>(
                id(name),
                (type, abilityId) -> new CreamMeleeAbility(
                        type, abilityId, style));
    }

    private static AbilityType<StandEntityHeavyPunchAbility>
            heavyPunchAbilityType(String name) {
        return new AbilityType<>(
                id(name), StandEntityHeavyPunchAbility::new);
    }

    private static AbilityType<CreamVoidAbility>
            voidAbilityType(String name, Kind kind) {
        return new AbilityType<>(
                id(name),
                (type, abilityId) -> new CreamVoidAbility(
                        type, abilityId, kind));
    }

    private static AbilityType<CreamEatAbility>
            eatAbilityType(String name, boolean stack) {
        return new AbilityType<>(
                id(name),
                (type, abilityId) -> new CreamEatAbility(
                        type, abilityId, stack));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(
            String name) {
        return SOUND_EVENTS.register(
                name, SoundEvent::createVariableRangeEvent);
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
                    "RotP Cream requires ROTP addon ABI 1 with "
                            + "namespaced ability resources and "
                            + "Stand visual contexts.");
        }
    }

    private static CreamStandType createStand(
            ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addHumanoidStandStuff()
                .addAbility("punch", CREAM_PUNCH_TYPE)
                .addAbility(
                        "barrage",
                        ModStandAbilities.BARRAGE,
                        barrage -> barrage.barrageHitSound(
                                CREAM_CLAW_SOUND))
                .addAbility(
                        CREAM_CLAW_NAME,
                        CREAM_CLAW_TYPE,
                        claw -> claw.punchImpactSound(
                                CREAM_CLAW_SOUND))
                .addAbility(
                        CREAM_FINISHER_PUNCH_NAME,
                        CREAM_FINISHER_PUNCH_TYPE,
                        finisher -> {
                            finisher.heavyPunchImpactSound(
                                    CREAM_SKULLCRUSH);
                            finisher.initIsFinisher(
                                    "heavy_punch");
                            finisher.resolveLevelToUnlock(1);
                        })
                .addAbility(
                        CREAM_DOUBLE_CLAW_FINISHER_NAME,
                        CREAM_DOUBLE_CLAW_FINISHER_TYPE,
                        finisher -> {
                            finisher.heavyPunchImpactSound(
                                    CREAM_HEAVY_CLAW_SOUND);
                            finisher.initIsFinisher(
                                    CREAM_HEAVY_CLAW_NAME);
                            finisher.resolveLevelToUnlock(1);
                        })
                .addAbility(
                        CREAM_HEAVY_CLAW_NAME,
                        CREAM_HEAVY_CLAW_TYPE,
                        claw -> claw.heavyPunchImpactSound(
                                CREAM_HEAVY_CLAW_SOUND))
                .addAbility(
                        "heavy_punch",
                        CREAM_HEAVY_PUNCH_TYPE)
                .addAbility(
                        "heavy_charged",
                        ModStandAbilities.HEAVY_CHARGED)
                .addAbility(
                        CREAM_CHOP_NAME,
                        CREAM_CHOP_TYPE,
                        chop -> chop.punchImpactSound(
                                CREAM_CHOP_SOUND))
                .addAbility(CREAM_CHOMP_NAME, CREAM_CHOMP_TYPE)
                .addAbility("guard", CREAM_BLOCK_TYPE)
                .addAbility(
                        CREAM_VOID_BALL_NAME,
                        CREAM_VOID_BALL_TYPE)
                .addAbility(
                        CREAM_VOID_BALL_CANCEL_NAME,
                        CREAM_VOID_BALL_CANCEL_TYPE)
                .addAbility(
                        CREAM_VOID_DASH_NAME,
                        CREAM_VOID_DASH_TYPE)
                .addAbility(
                        CREAM_EAT_ITEM_NAME,
                        CREAM_EAT_ITEM_TYPE)
                .addAbility(
                        CREAM_EAT_ITEM_STACK_NAME,
                        CREAM_EAT_ITEM_STACK_TYPE)
                .addAbility(
                        CREAM_SEMI_VOID_NAME,
                        CREAM_SEMI_VOID_TYPE)
                .addAbility(
                        CREAM_SEMI_VOID_CANCEL_NAME,
                        CREAM_SEMI_VOID_CANCEL_TYPE)
                .makeControlScheme("hotbar")
                    .bind(
                            "punch",
                            InputMethod.CLICK,
                            InputKey.LMB)
                    .bind("barrage", InputMethod.HOLD, InputKey.LMB)
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
                            "guard",
                            InputMethod.HOLD,
                            InputKey.RMB)
                    .makeHotbar(
                            0, USE_SPECIAL, SWITCH_SPECIAL)
                    .addToHotbar(
                            CREAM_CLAW_NAME,
                            0,
                            InputMethod.CLICK)
                    .addHotbarSlotVariation(
                            CREAM_HEAVY_CLAW_NAME,
                            CREAM_CLAW_NAME,
                            InputKey.Modifier.SHIFT,
                            InputMethod.CLICK)
                    .addToHotbar(
                            CREAM_CHOP_NAME,
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            CREAM_CHOMP_NAME,
                            0,
                            InputMethod.HOLD)
                    // 1.16 had no Shift variation here: the cancels come from
                    // CreamVoidAbility.replaceWithSubAbility while in the void.
                    .addToHotbar(
                            CREAM_VOID_BALL_NAME,
                            0,
                            InputMethod.HOLD)
                    .addToHotbar(
                            CREAM_SEMI_VOID_NAME,
                            0,
                            InputMethod.HOLD)
                    .addToHotbar(
                            CREAM_VOID_DASH_NAME,
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            CREAM_EAT_ITEM_NAME,
                            0,
                            InputMethod.CLICK)
                    .addHotbarSlotVariation(
                            CREAM_EAT_ITEM_STACK_NAME,
                            CREAM_EAT_ITEM_NAME,
                            InputKey.Modifier.SHIFT,
                            InputMethod.CLICK)
                .finalizeControlScheme()
                .makeControlScheme("keybinds")
                    .bind(
                            "punch",
                            InputMethod.CLICK,
                            InputKey.LMB)
                    .bind("barrage", InputMethod.HOLD, InputKey.LMB)
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
                            "guard",
                            InputMethod.HOLD,
                            InputKey.RMB)
                .finalizeControlScheme()
                .addSkill(StandUnlockableSkill.startingAbility(
                        "punch"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "barrage"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "heavy_punch"))
                .addSkill(StandUnlockableSkill.startingAbility(
                                "heavy_charged")
                        .prerequisiteSkill(
                                "heavy_punch"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "guard"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        CREAM_CLAW_NAME))
                .addSkill(StandUnlockableSkill.unlockableAbility(
                                CREAM_HEAVY_CLAW_NAME, 50)
                        .prerequisiteSkill(CREAM_CLAW_NAME))
                .addSkill(StandUnlockableSkill.unlockableAbility(
                                CREAM_FINISHER_PUNCH_NAME, 50)
                        .prerequisiteSkill(
                                "heavy_punch"))
                .addSkill(StandUnlockableSkill.unlockableAbility(
                                CREAM_DOUBLE_CLAW_FINISHER_NAME,
                                50)
                        .prerequisiteSkill(
                                CREAM_HEAVY_CLAW_NAME))
                .addSkill(StandUnlockableSkill.unlockableAbility(
                                CREAM_CHOP_NAME, 75)
                        .prerequisiteSkill(
                                "heavy_punch"))
                .addSkill(StandUnlockableSkill.unlockableAbility(
                                CREAM_CHOMP_NAME, 125)
                        .prerequisiteSkill(CREAM_CHOP_NAME))
                .addSkill(StandUnlockableSkill.unlockableAbility(
                                CREAM_VOID_BALL_NAME, 100)
                        .prerequisiteSkill(
                                CREAM_HEAVY_CLAW_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        CREAM_VOID_BALL_CANCEL_NAME))
                .addSkill(StandUnlockableSkill.unlockableAbility(
                                CREAM_SEMI_VOID_NAME, 125)
                        .prerequisiteSkill(
                                CREAM_VOID_BALL_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        CREAM_SEMI_VOID_CANCEL_NAME))
                .addSkill(StandUnlockableSkill.unlockableAbility(
                                CREAM_VOID_DASH_NAME, 100)
                        .prerequisiteSkill(
                                CREAM_SEMI_VOID_NAME))
                .addSkill(StandUnlockableSkill.unlockableAbility(
                                CREAM_EAT_ITEM_NAME, 50)
                        .prerequisiteSkill(CREAM_CLAW_NAME))
                .addSkill(StandUnlockableSkill.unlockableAbility(
                                CREAM_EAT_ITEM_STACK_NAME, 75)
                        .prerequisiteSkill(
                                CREAM_EAT_ITEM_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "manual_control"));

        return new CreamStandType(
                new StandStats.Builder()
                        .power(CreamSpec.POWER)
                        .speed(CreamSpec.SPEED)
                        .range(
                                CreamSpec.EFFECTIVE_RANGE,
                                CreamSpec.MAX_RANGE)
                        .durability(CreamSpec.DURABILITY)
                        .precision(CreamSpec.PRECISION)
                        .build(),
                moveset,
                CREAM_ENTITY.get(),
                id)
                .standControlType(StandControlType.CLOSE_RANGE_DIRECT)
                .manualControl(true)
                .distanceStrengthDecay(true)
                .standDimensions(
                        CreamSpec.ENTITY_WIDTH,
                        CreamSpec.ENTITY_HEIGHT)
                .summonShout(() -> VANILLA_ICE_CREAM)
                .summonSound(() -> CREAM_SUMMON)
                .unsummonSound(() -> CREAM_UNSUMMON);
    }
}
