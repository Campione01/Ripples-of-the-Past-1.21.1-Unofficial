package rotp.addons.ger;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Supplier;

import rotp.core.api.RotpAddonApi;
import rotp.core.api.client.render.AbilitySelectionVisualQuery;
import rotp.core.api.leap.LeapAccessPolicies;
import rotp.core.api.leap.LeapSource;
import rotp.core.api.soul.SoulResolveDecision;
import rotp.core.api.soul.SoulResolveEligibilityProviders;
import rotp.core.api.soul.SoulResolveQuery;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.util.sound.OstSoundList;
import rotp.addons.ger.ability.GerActionAnimations;
import rotp.addons.ger.ability.GerDisablerAbility;
import rotp.addons.ger.ability.GerEvolutionAbility;
import rotp.addons.ger.ability.GerLifeShotAbility;
import rotp.addons.ger.ability.GerReturnToZeroAbility;
import rotp.addons.ger.ability.GerReturnToZeroMarkAbility;
import rotp.addons.ger.ability.GerStandBarrageAbility;
import rotp.addons.ger.ability.GerStandHeavyPunchAbility;
import rotp.addons.ger.ability.GerStandPunchAbility;
import rotp.addons.ger.ability.GerUndiePunchAbility;
import rotp.addons.ger.entity.GerBeamEntity;
import rotp.addons.ger.entity.GerFieldEntity;
import rotp.addons.ger.entity.GerStandEntity;
import rotp.addons.ger.network.GerDisablerPayloads;
import rotp.addons.ger.network.GerRtzPayloads;
import rotp.addons.ger.network.GerUndiePayloads;
import rotp.addons.ger.state.GerLivingState;
import rotp.addons.ger.state.GerPlayerState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@Mod(GoldExperienceRequiemAddon.MOD_ID)
public final class GoldExperienceRequiemAddon {
    public static final String MOD_ID = "geraddon";
    private static final int REQUIRED_CORE_ABI = 1;

    public static final double POWER = 20.0D;
    public static final double SPEED = 15.0D;
    public static final double EFFECTIVE_RANGE = 10.0D;
    public static final double MAX_RANGE = 15.0D;
    public static final double DURABILITY = 20.0D;
    public static final double PRECISION = 10.0D;
    public static final float STAND_WIDTH = 0.35F;
    public static final float STAND_HEIGHT = 1.05F;

    public static final String GER_STAND_PUNCH_NAME =
            GerActionAnimations.PUNCH_ABILITY;
    public static final String GER_STAND_BARRAGE_NAME =
            GerActionAnimations.BARRAGE_ABILITY;
    public static final String GER_UNDIE_PUNCH_NAME =
            GerActionAnimations.UNDIE_ABILITY;
    public static final String GER_STAND_HEAVY_PUNCH_NAME =
            GerActionAnimations.HEAVY_ABILITY;
    public static final String GER_RETURN_TO_ZERO_NAME =
            "ger_return_to_zero";
    public static final String GER_RETURN_TO_ZERO_MARK_NAME =
            "ger_return_to_zero_mark";
    public static final String GER_LIFE_SHOT_NAME =
            GerActionAnimations.LIFE_SHOT_ABILITY;
    public static final String GER_LIFE_SHOT_MULTI_NAME =
            GerActionAnimations.LIFE_SHOT_MULTI_ABILITY;
    public static final String GER_DISABLER_NAME =
            GerActionAnimations.DISABLER_ABILITY;
    public static final String GER_EVOLUTION_NAME =
            "ger_evolution";

    public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES =
            DeferredRegister.create(
                    JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<AttachmentType<?>>
            ATTACHMENT_TYPES = DeferredRegister.create(
                    NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<StandType> STAND_TYPES =
            DeferredRegister.create(
                    JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<GerStandPunchAbility>>
            GER_STAND_PUNCH = ABILITY_TYPES.register(
                    GER_STAND_PUNCH_NAME,
                    key -> new AbilityType<>(
                            key, GerStandPunchAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<GerStandBarrageAbility>>
            GER_STAND_BARRAGE = ABILITY_TYPES.register(
                    GER_STAND_BARRAGE_NAME,
                    key -> new AbilityType<>(
                            key, GerStandBarrageAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<GerUndiePunchAbility>>
            GER_UNDIE_PUNCH = ABILITY_TYPES.register(
                    GER_UNDIE_PUNCH_NAME,
                    key -> new AbilityType<>(
                            key, GerUndiePunchAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<GerStandHeavyPunchAbility>>
            GER_STAND_HEAVY_PUNCH = ABILITY_TYPES.register(
                    GER_STAND_HEAVY_PUNCH_NAME,
                    key -> new AbilityType<>(
                            key,
                            GerStandHeavyPunchAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<GerReturnToZeroAbility>>
            GER_RETURN_TO_ZERO = ABILITY_TYPES.register(
                    GER_RETURN_TO_ZERO_NAME,
                    key -> new AbilityType<>(
                            key, GerReturnToZeroAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<GerReturnToZeroMarkAbility>>
            GER_RETURN_TO_ZERO_MARK = ABILITY_TYPES.register(
                    GER_RETURN_TO_ZERO_MARK_NAME,
                    key -> new AbilityType<>(
                            key, GerReturnToZeroMarkAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<GerLifeShotAbility>>
            GER_LIFE_SHOT = ABILITY_TYPES.register(
                    GER_LIFE_SHOT_NAME,
                    key -> new AbilityType<>(
                            key,
                            (type, id) -> new GerLifeShotAbility(
                                    type, id, 1)));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<GerLifeShotAbility>>
            GER_LIFE_SHOT_MULTI = ABILITY_TYPES.register(
                    GER_LIFE_SHOT_MULTI_NAME,
                    key -> new AbilityType<>(
                            key,
                            (type, id) -> new GerLifeShotAbility(
                                    type, id, 3)));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<GerDisablerAbility>>
            GER_DISABLER = ABILITY_TYPES.register(
                    GER_DISABLER_NAME,
                    key -> new AbilityType<>(
                            key, GerDisablerAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<GerEvolutionAbility>>
            GER_EVOLUTION = ABILITY_TYPES.register(
                    GER_EVOLUTION_NAME,
                    key -> new AbilityType<>(
                            key, GerEvolutionAbility::new));

    public static final Supplier<AttachmentType<GerPlayerState>>
            PLAYER_STATE = ATTACHMENT_TYPES.register(
                    "player_state",
                    () -> AttachmentType.serializable(
                                    holder -> holder instanceof Player
                                            ? new GerPlayerState()
                                            : null)
                            .copyOnDeath()
                            .sync(
                                    (holder, player) -> true,
                                    GerPlayerState.STREAM_CODEC)
                            .build());
    public static final Supplier<AttachmentType<GerLivingState>>
            LIVING_STATE = ATTACHMENT_TYPES.register(
                    "living_state",
                    () -> AttachmentType.serializable(
                                    holder ->
                                            holder
                                                    instanceof LivingEntity
                                            ? new GerLivingState()
                                            : null)
                            .copyOnDeath()
                            .build());

    public static final DeferredHolder<
            EntityType<?>, EntityType<GerStandEntity>>
            GER_STAND_ENTITY = ENTITY_TYPES.register(
                    "ger_stand",
                    key -> EntityType.Builder
                            .<GerStandEntity>of(
                                    GerStandEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(STAND_WIDTH, STAND_HEIGHT)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));
    public static final DeferredHolder<
            EntityType<?>, EntityType<GerBeamEntity>>
            BEAM_ENTITY = ENTITY_TYPES.register(
                    "life_shot_beam",
                    key -> EntityType.Builder
                            .<GerBeamEntity>of(
                                    GerBeamEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.1F, 0.1F)
                            .clientTrackingRange(192)
                            .updateInterval(1)
                            .build(key.toString()));
    public static final DeferredHolder<
            EntityType<?>, EntityType<GerFieldEntity>>
            FIELD_ENTITY = ENTITY_TYPES.register(
                    "ability_field",
                    key -> EntityType.Builder
                            .<GerFieldEntity>of(
                                    GerFieldEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.1F, 0.1F)
                            .clientTrackingRange(192)
                            .updateInterval(1)
                            .build(key.toString()));

    public static final DeferredHolder<SoundEvent, SoundEvent>
            LIFE_SHOT_SOUND = sound("life_shot");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            PUNCH_HIT_SOUND = sound("punch_hit");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            GER_SUMMON_SOUND = sound("ger_summon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            GER_SUMMON_SHOUT = sound("ger_summon_shout");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            GER_UNSUMMON_SOUND = sound("ger_unsummon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            GER_BARRAGE_SHOUT = sound("ger_barrage_shout");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            RETURN_TO_ZERO_SOUND = sound("return_to_zero");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            GER_HEAVY_PUNCH_SOUND = sound("ger_heavy_punch");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            GER_PUNCH_SOUND = sound("ger_punch");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            RTZ_END_SOUND = sound("rtz_end");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            GER_EVOLUTION_INSERT = sound("ger_evolution_insert");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            GER_EVOLUTION_VOICELINE1 =
                    sound("ger_evolution_voiceline1");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            GER_EVOLUTION_VOICELINE2 =
                    sound("ger_evolution_voiceline2");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            GER_DISABLER_SOUND = sound("ger_disabler");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            GER_EVOLUTION_END = sound("ger_evolution_end");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            GER_OST = sound("ger_ost");
    public static final OstSoundList GER_OST_LIST =
            new OstSoundList(id("ger_ost"), SOUND_EVENTS);

    public static final DeferredHolder<StandType, EntityStandType>
            GER_STAND = STAND_TYPES.register(
                    "ger_stand",
                    GoldExperienceRequiemAddon::createStand);

    public GoldExperienceRequiemAddon(IEventBus modEventBus) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_VISUAL_CONTEXT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_POWER_TRANSITIONS_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_SOUL_RESOLVE_ELIGIBILITY_PROVIDERS_V1)
                // the lockdowns interrupt the user's Stand-power slot too
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_POWER_ACTION_SLOT_V1)
                // the lockdowns refuse in the core's condition checks
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ABILITY_CONDITION_REFUSALS_V1)
                // canFireReleasedHold (R146 core held-action rechecks)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ENTITY_ACTION_HOOKS_V1)) {
            throw new IllegalStateException(
                    "Gold Experience Requiem requires RotP addon "
                            + "ABI 1 with namespaced abilities, Stand "
                            + "visual contexts, and Stand power "
                            + "transitions plus soul Resolve "
                            + "eligibility providers, the Stand-power "
                            + "action slot, ability condition refusals "
                            + "and entity action hooks.");
        }

        SoulResolveEligibilityProviders.register(
                id("undie_soul_resolve"),
                GoldExperienceRequiemAddon::undieSoulResolveDecision);
        // The leap packet has no alive check; a dying Undie victim stays
        // still until it is pulled back.
        LeapAccessPolicies.register(
                id("undie_leap_access"),
                query -> query.user() != null
                        && query.user().hasData(LIVING_STATE)
                        && livingState(query.user()).inUndieDeath());
        // 1.16 DisablerLeapMixin: StandPower.canLeap was false for a
        // ger_disabler user, on its client too; other powers still leapt.
        LeapAccessPolicies.register(
                id("disabler_leap_access"),
                query -> query.source() == LeapSource.STAND
                        && query.user() != null
                        && GoldExperienceRequiemGameEvents.actionsDisabled(
                                query.user()));
        ABILITY_TYPES.register(modEventBus);
        ATTACHMENT_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        modEventBus.addListener(
                GoldExperienceRequiemModEvents::registerAttributes);
        modEventBus.addListener(
                GoldExperienceRequiemModEvents::commonSetup);
        modEventBus.addListener(GerRtzPayloads::register);
        modEventBus.addListener(GerUndiePayloads::register);
        modEventBus.addListener(GerDisablerPayloads::register);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    // 1.16 GoldExperienceEntityLifeshot was a StandEntityActionModifier (078 InitStands :86), so a selected Life Shot
    // was framed green while usable; the core's green rule keys the Gold Experience Stand's own ability ids.
    public static OptionalInt lifeShotSelectionTint(AbilitySelectionVisualQuery query) {
        return query.ability().abilityType == ModStandAbilities.GE_ENTITY_LIFESHOT.get()
                && GER_STAND.getId().equals(query.ability().getAbilityId().powerTypeId())
                && query.conditionCheck().isPositive()
                ? OptionalInt.of(LIFE_SHOT_GREEN) : OptionalInt.empty();
    }

    private static final int LIFE_SHOT_GREEN = 0xFF00FF00;

    public static GerPlayerState playerState(Player player) {
        return player.getData(PLAYER_STATE);
    }

    public static GerLivingState livingState(
            LivingEntity entity) {
        return entity.getData(LIVING_STATE);
    }

    public static boolean isGerUser(LivingEntity entity) {
        StandPower power = entity != null
                ? StandPower.get(entity)
                : null;
        return power != null
                && power.hasPower()
                && power.getPowerType() == GER_STAND.get();
    }

    private static SoulResolveDecision undieSoulResolveDecision(
            SoulResolveQuery query) {
        LivingEntity origin = query.origin();
        if (origin == null) {
            return SoulResolveDecision.PASS;
        }
        UUID caster = livingState(origin).undieSoulCaster();
        if (caster == null) {
            return SoulResolveDecision.PASS;
        }
        LivingEntity standUser = StandUtil.getStandUser(query.target());
        if (standUser == null
                || !caster.equals(standUser.getUUID())) {
            return SoulResolveDecision.DENY;
        }
        return query.resolveCanLevelUp()
                || query.targetStandPower().getResolveLevel()
                        < query.targetStandPower().getMaxResolveLevel()
                ? SoulResolveDecision.ALLOW
                : SoulResolveDecision.DENY;
    }

    public static GerStandEntity activeStand(
            LivingEntity entity) {
        StandPower power = entity != null
                ? StandPower.get(entity)
                : null;
        return power != null
                && power.hasPower()
                && power.isSummoned()
                && power.getPowerType() == GER_STAND.get()
                && power.getSummonedStandEntity()
                        instanceof GerStandEntity stand
                && stand.isAlive()
                && !stand.isRemoved()
                ? stand
                : null;
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(
            String name) {
        return SOUND_EVENTS.register(
                name, SoundEvent::createVariableRangeEvent);
    }

    private static EntityStandType createStand(
            ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addHumanoidStandStuff()
                .addAbility(
                        GER_STAND_PUNCH_NAME,
                        GER_STAND_PUNCH,
                        punch -> {
                            punch.punchImpactSound(GER_PUNCH_SOUND);
                            punch.detachedLightCounterKnockback();
                            punch.laterPhaseEntryTiming();
                        })
                .addAbility(
                        GER_STAND_BARRAGE_NAME,
                        GER_STAND_BARRAGE,
                        // 1.16 standSound(GER_BARRAGE_SHOUT) also cried
                        // with the arms only.
                        barrage -> barrage
                                .barrageHitSound(PUNCH_HIT_SOUND)
                                .barrageCrySound(
                                        GER_BARRAGE_SHOUT)
                                .barrageCryInArmsOnly())
                .addAbility(
                        GER_STAND_HEAVY_PUNCH_NAME,
                        GER_STAND_HEAVY_PUNCH,
                        punch -> {
                            punch.heavyPunchImpactSound(
                                    GER_HEAVY_PUNCH_SOUND);
                            punch.setDefaultPhaseLength(
                                    ActionPhase.PERFORM, 1);
                            punch.detachedPunchKnockback();
                            punch.recoveryPhaseEntryTiming();
                        })
                .addAbility(
                        "heavy_charged",
                        ModStandAbilities.HEAVY_CHARGED)
                .addAbility(
                        GER_UNDIE_PUNCH_NAME,
                        GER_UNDIE_PUNCH,
                        punch -> {
                            punch.heavyPunchImpactSound(
                                    GER_HEAVY_PUNCH_SOUND);
                            punch.detachedPunchKnockback();
                            punch.recoveryPhaseEntryTiming();
                        })
                .addAbility("guard", ModStandAbilities.GUARD)
                .addAbility("grab", ModStandAbilities.GRAB)
                .addAbility(
                        "grab_throw",
                        ModStandAbilities.GRAB_THROW)
                .addAbility(
                        "grab_punch",
                        GER_STAND_PUNCH,
                        punch -> {
                            punch.punchImpactSound(PUNCH_HIT_SOUND)
                                    .standCrySound(GER_PUNCH_SOUND);
                            punch.initIsGrabVariation();
                        })
                .addAbility(
                        "grab_barrage",
                        GER_STAND_BARRAGE,
                        barrage -> {
                            barrage.barrageHitSound(PUNCH_HIT_SOUND)
                                    .barrageCrySound(
                                            GER_BARRAGE_SHOUT);
                            barrage.initIsGrabVariation();
                        })
                .addAbility(
                        "grab_heavy_punch",
                        GER_STAND_HEAVY_PUNCH,
                        punch -> {
                            punch.heavyPunchImpactSound(
                                    PUNCH_HIT_SOUND);
                            punch.heavyPunchCrySound(
                                    GER_HEAVY_PUNCH_SOUND);
                            punch.initIsGrabVariation();
                        })
                .addAbility(
                        "choose_lifeform",
                        ModStandAbilities.GE_CHOOSE_LIFEFORM)
                .addAbility(
                        "create_lifeform",
                        ModStandAbilities.GE_CREATE_LIFEFORM)
                .addAbility(
                        "mark_item",
                        ModStandAbilities.GE_MARK_ITEM)
                .addAbility(
                        "bone_meal",
                        ModStandAbilities.GE_BONE_MEAL)
                .addAbility(
                        "life_detector",
                        ModStandAbilities.GE_LIFE_DETECTOR)
                .addAbility("heal", ModStandAbilities.GE_HEAL)
                .addAbility(
                        "healing_item",
                        ModStandAbilities.GE_HEALING_ITEM)
                .addAbility(
                        "heal_other",
                        ModStandAbilities.GE_HEAL_OTHER)
                .addAbility(
                        "lifeshot",
                        ModStandAbilities.GE_ENTITY_LIFESHOT)
                .addAbility(
                        "lifeshot_punch",
                        ModStandAbilities.GE_LIFESHOT_PUNCH,
                        punch -> {
                            punch.heavyPunchImpactSound(ModSoundEvents
                                    .GOLD_EXPERIENCE_PUNCH_HEAVY);
                            punch.heavyPunchCrySound(ModSoundEvents
                                    .GOLD_EXPERIENCE_MUDA_LONG);
                            punch.heavyPunchPerformSound(ModSoundEvents
                                    .GOLD_EXPERIENCE_PUNCH_HEAVY_EXTRA);
                            punch.initIsFinisher(
                                    GER_STAND_HEAVY_PUNCH_NAME);
                            punch.resolveLevelToUnlock(1);
                            punch.setDefaultPhaseLength(
                                    ActionPhase.PERFORM, 1);
                            punch.detachedPunchKnockback();
                        })
                .addAbility(
                        "revert_lifeform",
                        ModStandAbilities.GE_REVERT_LIFEFORM)
                .addAbility(
                        GER_LIFE_SHOT_NAME, GER_LIFE_SHOT)
                .addAbility(
                        GER_LIFE_SHOT_MULTI_NAME,
                        GER_LIFE_SHOT_MULTI)
                .addAbility(
                        GER_RETURN_TO_ZERO_NAME,
                        GER_RETURN_TO_ZERO)
                .addAbility(
                        GER_RETURN_TO_ZERO_MARK_NAME,
                        GER_RETURN_TO_ZERO_MARK)
                .addAbility(
                        GER_DISABLER_NAME, GER_DISABLER)
                .makeControlScheme("hotbar")
                    .bind(
                            GER_STAND_PUNCH_NAME,
                            InputMethod.CLICK,
                            InputKey.LMB)
                    .bind(
                            GER_STAND_BARRAGE_NAME,
                            InputMethod.HOLD,
                            InputKey.LMB)
                    .bind(
                            GER_STAND_HEAVY_PUNCH_NAME,
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
                            0, USE_SPECIAL, SWITCH_SPECIAL)
                    .addToHotbar(
                            "choose_lifeform",
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            "create_lifeform",
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            "revert_lifeform",
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            "mark_item",
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            "bone_meal",
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            "life_detector",
                            0,
                            InputMethod.HOLD)
                    .addToHotbar(
                            "heal",
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            "heal_other",
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            "healing_item",
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            GER_UNDIE_PUNCH_NAME,
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            GER_LIFE_SHOT_NAME,
                            0,
                            InputMethod.CLICK)
                    .addHotbarSlotVariation(
                            GER_LIFE_SHOT_MULTI_NAME,
                            GER_LIFE_SHOT_NAME,
                            InputKey.Modifier.SHIFT,
                            InputMethod.HOLD)
                    .addToHotbar(
                            GER_RETURN_TO_ZERO_NAME,
                            0,
                            InputMethod.CLICK)
                    .addHotbarSlotVariation(
                            GER_RETURN_TO_ZERO_MARK_NAME,
                            GER_RETURN_TO_ZERO_NAME,
                            InputKey.Modifier.SHIFT,
                            InputMethod.CLICK)
                    .addToHotbar(
                            GER_DISABLER_NAME,
                            0,
                            InputMethod.CLICK)
                .finalizeControlScheme()
                .makeControlScheme("keybinds")
                    .bind(
                            GER_STAND_PUNCH_NAME,
                            InputMethod.CLICK,
                            InputKey.LMB)
                    .bind(
                            GER_STAND_BARRAGE_NAME,
                            InputMethod.HOLD,
                            InputKey.LMB)
                    .bind(
                            GER_STAND_HEAVY_PUNCH_NAME,
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
                .addSkill(StandUnlockableSkill.startingAbility(
                        GER_STAND_PUNCH_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        GER_STAND_BARRAGE_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        GER_STAND_HEAVY_PUNCH_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                                "heavy_charged")
                        .prerequisiteSkill(
                                GER_STAND_HEAVY_PUNCH_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        GER_UNDIE_PUNCH_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "guard"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "grab"))
                .addSkill(StandUnlockableSkill.unlockableAbility(
                                "grab_throw", 100)
                        .withAbility(
                                "grab_punch",
                                "grab_barrage",
                                "grab_heavy_punch")
                        .prerequisiteSkill("grab"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "choose_lifeform"))
                .addSkill(StandUnlockableSkill.startingAbility(
                                "create_lifeform")
                        .withAbility("revert_lifeform"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "mark_item"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "bone_meal"))
                .addSkill(StandUnlockableSkill.unlockableAbility(
                        "life_detector", 200))
                .addSkill(StandUnlockableSkill.unlockableAbility(
                        "heal", 250))
                .addSkill(StandUnlockableSkill.unlockableAbility(
                                "heal_other", 300)
                        .withAbility("healing_item")
                        .prerequisiteSkill("heal"))
                .addSkill(StandUnlockableSkill.unlockableAbility(
                                "lifeshot", 150)
                        .withAbility("lifeshot_punch")
                        .prerequisiteSkill(
                                GER_STAND_HEAVY_PUNCH_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                                GER_LIFE_SHOT_NAME)
                        .withAbility(GER_LIFE_SHOT_MULTI_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                                GER_RETURN_TO_ZERO_NAME)
                        .withAbility(
                                GER_RETURN_TO_ZERO_MARK_NAME))
                .addSkill(StandUnlockableSkill.startingAbility(
                        GER_DISABLER_NAME))
                .addHumanoidStandSkills();

        return new EntityStandType(
                new StandStats.Builder()
                        .power(POWER)
                        .speed(SPEED)
                        .range(EFFECTIVE_RANGE, MAX_RANGE)
                        .durability(DURABILITY)
                        .precision(PRECISION)
                        .randomWeight(0.0D)
                        .build(),
                moveset,
                GER_STAND_ENTITY.get(),
                id)
                .standControlType(StandControlType.CLOSE_RANGE_DIRECT)
                .manualControl(true)
                .distanceStrengthDecay(true)
                .standDimensions(STAND_WIDTH, STAND_HEIGHT)
                .summonShout(() -> GER_SUMMON_SHOUT)
                .summonSound(() -> GER_SUMMON_SOUND)
                .unsummonSound(() -> GER_UNSUMMON_SOUND)
                .ost(() -> GER_OST_LIST);
    }
}
