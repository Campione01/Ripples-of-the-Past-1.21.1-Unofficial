package rotp.addons.reworked.ability;

import javax.annotation.Nullable;

import rotp.core.api.timestop.TimeStopBehaviorPolicy;
import rotp.core.api.timestop.TimeStopStartupCostContext;
import rotp.core.api.timestop.TimeStopStartupCostDecision;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.entityattachment.custom_effect.EntityCustomEffectType;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.TrainableAbility;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.effect.StandEffectInstance;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.timestop.TimeStopCooldowns;
import rotp.core.subsystems.timestop.TimeStopLearning;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.impl.stands.magiciansred.MagiciansRedFlameBurstAbility;
import rotp.core.impl.stands.magiciansred.MagiciansRedKickAbility;
import rotp.core.impl.stands.starplatinum.HeavyPunchUppercutAbility;
import rotp.core.impl.stands.theworld.TheWorldTSPunchAbility;
import rotp.core.impl.stands.theworld.TimeResumeAbility;
import rotp.core.impl.stands.theworld.TimeStopAbility;
import rotp.core.impl.stands.theworld.TimeStopBlinkAbility;

import rotp.addons.reworked.ReworkedAddon;
import rotp.addons.reworked.ReworkedConfig;
import rotp.addons.reworked.network.ReworkedManualInput;
import net.minecraft.core.Holder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class ReworkedStandAbilities {
    private static final String CORE_NAMESPACE = "jojo_ripples";
    private static final String LEGACY_NAMESPACE = "jojo";
    public static final String STAR_PLATINUM_BLINK_HEAVY_NAME =
            "rejojo_star_platinum_heavy_punch_ts";
    public static final String STAR_PLATINUM_BLINK_UPPERCUT_NAME =
            "rejojo_star_platinum_uppercut_ts";
    public static final String THE_WORLD_BLINK_RECOVERY_NAME =
            "rejojo_the_world_ts_blink_recovery";
    public static final String THE_WORLD_TS_PUNCH_NAME = "ts_punch";
    public static final int THE_WORLD_TS_PUNCH_RESOLVE_LEVEL = 3;
    public static final String THE_WORLD_TIME_STOP_NAME =
            "the_world_time_stop";
    public static final String THE_WORLD_TIME_STOP_BLINK_NAME =
            "the_world_ts_blink";
    // 1.16 noResolveUnlock() + power.unlockAction: a hidden skill
    public static final String MAGICIANS_RED_FLAME_BURST_SKILL =
            "rejojo_magicians_red_flame_burst";
    public static final float MAGICIANS_RED_PUNCH_MAX_TRAINING = 1.0F;
    public static final float MAGICIANS_RED_PUNCH_HITS_TO_TRAIN = 40.0F;
    // 1.16 Reworked RE_*_TIME_STOP builders: staminaCost(300),
    // staminaCostTick(8.875)
    public static final float TIME_STOP_STAMINA_COST = 300.0F;
    public static final float TIME_STOP_STAMINA_COST_TICK = 8.875F;
    // 1.16 holdToFire: RE_STAR_PLATINUM_TIME_STOP 30, RE_THE_WORLD_TIME_STOP
    // 35 (a TheWorldTimeStop, so 20 from Resolve level 4).
    public static final int STAR_PLATINUM_HOLD_TO_FIRE_TICKS = 30;
    public static final int THE_WORLD_HOLD_TO_FIRE_TICKS = 35;
    public static final int THE_WORLD_RESOLVE_HOLD_TO_FIRE_TICKS = 20;
    // The core prices a time stop's start from 225; a Reworked one starts
    // from 300 (scaled like the core cost for a released charge).
    public static final TimeStopBehaviorPolicy TIME_STOP_COST_POLICY =
            new TimeStopBehaviorPolicy() {
                @Override
                public TimeStopStartupCostDecision startupCost(
                        TimeStopStartupCostContext context) {
                    return context.power().getAbility(
                                    context.abilityId().nameInMoveset())
                                    instanceof ReworkedTimeStopAbility
                            ? TimeStopStartupCostDecision.override(
                                    context.defaultCost()
                                            * TIME_STOP_STAMINA_COST
                                            / TimeStopLearning
                                                    .BASE_STAMINA_COST)
                            : TimeStopStartupCostDecision.pass();
                }
            };

    private ReworkedStandAbilities() {}

    private static ResourceLocation configuredSprite(
            String donorSprite,
            String fallbackNamespace,
            String fallbackSprite) {
        boolean donorIcons =
                ReworkedConfig.ENABLE_NEW_TS_ICONS.get();
        return ResourceLocation.fromNamespaceAndPath(
                donorIcons ? CORE_NAMESPACE : fallbackNamespace,
                donorIcons ? donorSprite : fallbackSprite);
    }

    public static final class ReworkedTimeStopAbility
            extends TimeStopAbility {
        private final String donorSprite;

        public ReworkedTimeStopAbility(
                AbilityType<?> type, AbilityId id) {
            super(type, id);
            donorSprite = type.registryKey.getPath();
            // Both 1.16 Reworked time stop builders call
            // ignoresPerformerStun(); the replacement does not inherit the
            // core time_stop customizer that sets it.
            setIgnoresPerformerStun();
            // 1.16 ReTheWorldTimeStop keeps TheWorldTimeStop's rule: a hit
            // during the charge drops it. The Star Platinum one does not.
            if (THE_WORLD_TIME_STOP_NAME.equals(donorSprite)) {
                setCancelHeldOnGettingAttacked();
            }
        }

        @Override
        public ResourceLocation getSpriteId(Power<?> context) {
            return configuredSprite(
                    donorSprite, CORE_NAMESPACE, "time_stop");
        }

        // The core charge is 40 (Star Platinum) / 30 (The World); in
        // stopped time it is 0 and stays 0.
        @Override
        public void initActionFromConfig(
                EntityActionInstance action,
                Level level,
                LivingEntity powerUser,
                LivingEntity performer) {
            super.initActionFromConfig(
                    action, level, powerUser, performer);
            if (action.phasesLength.getFloat(
                    ActionPhase.BUTTON_CHARGE) <= 0.0F) {
                return;
            }
            int chargeTicks = STAR_PLATINUM_HOLD_TO_FIRE_TICKS;
            if (THE_WORLD_TIME_STOP_NAME.equals(donorSprite)) {
                StandPower power = powerUser != null
                        ? PowerClass.STAND.get(powerUser)
                        : null;
                // 1.16 TheWorldTimeStop.getHoldDurationToFire: the stored level only
                chargeTicks = power != null
                                && power.getResolveLevel() >= 4
                        ? THE_WORLD_RESOLVE_HOLD_TO_FIRE_TICKS
                        : THE_WORLD_HOLD_TO_FIRE_TICKS;
            }
            action.phasesLength.put(
                    ActionPhase.BUTTON_CHARGE, chargeTicks);
        }

        @Override
        public ConditionCheck getResolveUnlockConditionCheck(
                Power<?> context) {
            StandPower power = PowerClass.STAND.cast(context);
            if (power == null || power.getCurTypeData() == null) {
                return ConditionCheck.createNegative(
                        "not_unlocked");
            }
            if (power.getCurTypeData().isSkillUnlocked(
                    TimeStopLearning.TIME_STOP)) {
                return ConditionCheck.POSITIVE;
            }
            return ConditionCheck.createNegative(
                    "not_unlocked");
        }
    }

    public static final class ReworkedTimeStopBlinkAbility
            extends TimeStopBlinkAbility {
        private final String donorSprite;

        public ReworkedTimeStopBlinkAbility(
                AbilityType<?> type, AbilityId id) {
            super(type, id);
            donorSprite = type.registryKey.getPath();
            // 1.16 ReTimeStopInstant costs 0.8 of its Reworked time stop.
            setBaseTimeStopStaminaCosts(
                    TIME_STOP_STAMINA_COST, TIME_STOP_STAMINA_COST_TICK);
            // RE_THE_WORLD_TIME_STOP_BLINK teleports behind the target; the
            // replacement does not get the core time_stop_blink customizer.
            if (THE_WORLD_TIME_STOP_BLINK_NAME.equals(donorSprite)) {
                setTeleportBehindEntity();
            }
        }

        // With the new icons off, 1.16 showed the core's blink icon.
        @Override
        public ResourceLocation getSpriteId(Power<?> context) {
            return configuredSprite(
                    donorSprite, CORE_NAMESPACE, "time_stop_blink");
        }
    }

    public static final class ReworkedTimeResumeAbility
            extends TimeResumeAbility {
        private final String donorSprite;

        public ReworkedTimeResumeAbility(
                AbilityType<?> type, AbilityId id) {
            super(type, id);
            donorSprite = type.registryKey.getPath();
        }

        @Override
        public ResourceLocation getSpriteId(Power<?> context) {
            return configuredSprite(
                    donorSprite, CORE_NAMESPACE, "time_stop");
        }
    }

    public static final class ReworkedStarPlatinumHeavyPunchAbility
            extends StandEntityHeavyPunchAbility {
        public ReworkedStarPlatinumHeavyPunchAbility(
                AbilityType<?> type, AbilityId id) {
            super(type, id);
            heavyPunchImpactSound(
                    ModSoundEvents.STAR_PLATINUM_PUNCH_HEAVY);
            heavyPunchCrySound(
                    ModSoundEvents.STAR_PLATINUM_ORA_LONG);
            // 1.16 ReStarPlatinumHeavyPunch keeps the builder's one-tick
            // perform, StandEntityPunch's separate success and tail pulses
            // and the recovery sampled on entry; the replacement does not
            // get the core heavy_punch customizer that sets them.
            setDefaultPhaseLength(ActionPhase.PERFORM, 1);
            detachedPunchKnockback();
            recoveryPhaseEntryTiming();
        }

        // 1.16 ReStarPlatinumHeavyPunch.replaceAction (:48-53): Shift and the
        // heavy key in the Stand's windup give the blink punch before the
        // key's own action is asked canClickDuringTask. The heavy key stays on
        // the running heavy punch, which the core refuses pressed again, so
        // the blink is resolved here, before that refusal.
        @Override
        public Ability replaceWithSubAbility(
                Power<?> context, AvailableAbilities abilities) {
            StandPower power = PowerClass.STAND.cast(context);
            String repeat = power != null
                    ? getStarPlatinumBlinkRepeat(
                            power.getSummonedStandEntity())
                    : null;
            Ability blink = repeat != null ? power.getAbility(repeat) : null;
            if (blink != null && blink.isAbilityAvailable(context)) {
                return blinkOnGrabKeyToo(context, abilities, blink);
            }
            return super.replaceWithSubAbility(context, abilities);
        }
    }

    // With Shift held the HUD draws the Shift+RMB binds (the grab click),
    // whose press the server swaps for the blink (ReworkedGameEvents
    // .onAbilityKeyPress); the client resolves that slot here as well.
    private static Ability blinkOnGrabKeyToo(
            Power<?> context, @Nullable AvailableAbilities abilities,
            Ability blink) {
        if (abilities != null) {
            abilities.replaceOtherAbilityWith(context, "grab", blink);
        }
        return blink;
    }

    public static final class ReworkedStarPlatinumUppercutAbility
            extends HeavyPunchUppercutAbility {
        public ReworkedStarPlatinumUppercutAbility(
                AbilityType<?> type, AbilityId id) {
            super(type, id);
            heavyPunchImpactSound(
                    ModSoundEvents.STAR_PLATINUM_PUNCH_HEAVY);
            heavyPunchCrySound(
                    ModSoundEvents.STAR_PLATINUM_ORA_LONG);
            initIsFinisher();
            resolveLevelToUnlock(1);
            // 1.16 ReStarPlatinumUppercut, as the heavy punch.
            setDefaultPhaseLength(ActionPhase.PERFORM, 1);
            detachedPunchKnockback();
            recoveryPhaseEntryTiming();
        }

        // 1.16 ReStarPlatinumUppercut.replaceAction: Shift and the heavy key
        // in the uppercut's windup give the blink uppercut. The heavy key
        // stays on the running uppercut, which is refused once the abilities
        // are resolved, so the blink is resolved here, before that refusal.
        // ReStarPlatinumHeavyPunch.replaceAction (:48-53) asked for its blink
        // before the finisher variation, so a plain heavy punch in its windup
        // gives the blink heavy punch even when the key reaches the uppercut.
        @Override
        public Ability replaceWithSubAbility(
                Power<?> context, AvailableAbilities abilities) {
            StandPower power = PowerClass.STAND.cast(context);
            String repeat = power != null
                    ? getStarPlatinumBlinkRepeat(
                            power.getSummonedStandEntity())
                    : null;
            if (repeat != null) {
                Ability blink = power.getAbility(repeat);
                if (blink != null && blink.isAbilityAvailable(context)) {
                    return blinkOnGrabKeyToo(context, abilities, blink);
                }
                // 1.16 Action.getVisibleAction gave a key whose blink was not
                // unlocked its own action, ReStarPlatinumHeavyPunch, whose
                // replaceAction asked for the blink before the finisher
                Ability heavy = power.getAbility("heavy_punch");
                if (STAR_PLATINUM_BLINK_HEAVY_NAME.equals(repeat)
                        && heavy != null) {
                    return heavy;
                }
            }
            return super.replaceWithSubAbility(context, abilities);
        }
    }

    public static final class ReworkedStarPlatinumBlinkPunchAbility
            extends StandEntityHeavyPunchAbility {
        private final boolean uppercut;

        public ReworkedStarPlatinumBlinkPunchAbility(
                AbilityType<?> type, AbilityId id) {
            super(type, id);
            uppercut = type.registryKey.getPath()
                    .contains("uppercut");
            createActionObj = uppercut
                    ? BlinkUppercut::new
                    : BlinkHeavyPunch::new;
            heavyPunchImpactSound(
                    ModSoundEvents.STAR_PLATINUM_PUNCH_HEAVY);
            heavyPunchCrySound(
                    ModSoundEvents.STAR_PLATINUM_ORA_LONG);
            partsRequired(StandPart.MAIN_BODY, StandPart.ARMS);
            isSubAbility = true;
            // 1.16 StarPlatinumBlinkPunch struck with the punch of the heavy
            // attack it was built with; this ability's action is that punch
            // and is never set on the Stand.
            setDefaultPhaseLength(ActionPhase.PERFORM, 1);
            detachedPunchKnockback();
        }

        @Override
        public boolean addToControlSchemeEditing() {
            return false;
        }

        @Override
        public boolean shouldSetCooldownOnKeyPress(
                InputMethod inputMethod) {
            return false;
        }

        @Override
        public String getSpriteName(Power<?> context) {
            return uppercut
                    ? "star_platinum_uppercut_ts"
                    : "star_platinum_heavy_punch_ts";
        }

        @Override
        public ResourceLocation getSpriteId(Power<?> context) {
            String sprite = getSpriteName(context);
            return configuredSprite(
                    sprite, LEGACY_NAMESPACE, sprite);
        }

        @Override
        public ConditionCheck getUnlockConditionCheck(
                Power<?> context) {
            Ability timeStop = context != null
                    ? context.getAbility("time_stop")
                    : null;
            return timeStop != null
                    ? timeStop.getUnlockConditionCheck(context)
                    : ConditionCheck.createNegative("not_unlocked");
        }

        // 1.16 kept a task's modifiers in a set: a second blink pressed in the
        // same windup added nothing.
        @Override
        protected ConditionCheck checkStandEntityConditions(
                StandPower power, StandEntity stand) {
            return !power.isAbilityOnCooldown(
                            TimeStopCooldowns.TIME_STOP_BLINK)
                            && isEligibleRepeat(stand)
                            && !hasBlinkModifier(stand.getCurStandAction())
                    ? ConditionCheck.POSITIVE
                    : ConditionCheck.NEGATIVE;
        }

        // 1.16 StandEntityActionModifier.perform: the press adds the blink to
        // the Stand's running task, the heavy punch and the uppercut too, on
        // the server, and starts no task of its own, so that task keeps its
        // target.
        @Override
        public HeldInput onKeyPress(
                Level level,
                LivingEntity user,
                FriendlyByteBuf extraClientInput,
                InputMethod inputMethod,
                float clickHoldResolveTime,
                BufferingState bufferingState) {
            StandPower power = StandPower.get(user);
            StandEntity stand = power != null
                    ? power.getSummonedStandEntity()
                    : null;
            // the key press event swaps the blink in after the key's checks
            if (!level.isClientSide() && stand != null
                    && getUnlockConditionCheck(power).isPositive()
                    && checkConditions(power).isPositive()
                    && checkStandEntityConditions(power, stand)
                            .isPositive()) {
                power.userStandEffects.addEffect((uppercut
                                ? ReworkedAddon.STAR_PLATINUM_UPPERCUT_BLINK_MODIFIER
                                : ReworkedAddon.STAR_PLATINUM_BLINK_MODIFIER)
                        .get().create(level));
                bufferingState.isActionSuccess = true;
            }
            return null;
        }

        @Override
        public boolean shouldBufferInput(
                LivingComponentAction performerAction) {
            EntityActionInstance action =
                    performerAction.getAction();
            return !(action != null
                    && isEligibleRepeat(
                            action.getPerformer()
                                    instanceof StandEntity stand
                                            ? stand
                                            : null));
        }

        private boolean isEligibleRepeat(StandEntity stand) {
            return (uppercut
                            ? STAR_PLATINUM_BLINK_UPPERCUT_NAME
                            : STAR_PLATINUM_BLINK_HEAVY_NAME)
                    .equals(getStarPlatinumBlinkRepeat(stand));
        }
    }

    // 1.16 ReStarPlatinumHeavyPunch / ReStarPlatinumUppercut.replaceAction:
    // the blink punch a repeat heavy press becomes in WINDUP with the blink
    // modifier held, or null. Shared by the key press and the buffered repeat.
    // The heavy one took any running task but the uppercut.
    @Nullable
    public static String getStarPlatinumBlinkRepeat(
            @Nullable StandEntity stand) {
        if (stand == null || stand.getUser() == null
                || !ReworkedManualInput.isBlinkModifierHeld(
                        stand.getUser(), stand)
                || ReworkedTimeSkip.isTimeStopped(
                        stand.level(), stand.getUser())) {
            return null;
        }
        EntityActionInstance action = stand.getCurStandAction();
        if (action == null
                || action.getPhase() != ActionPhase.WINDUP
                || action.ability == null
                || action.ability.getAbilityId() == null) {
            return null;
        }
        return "finisher_uppercut".equals(
                        action.ability.getAbilityId().nameInMoveset())
                ? STAR_PLATINUM_BLINK_UPPERCUT_NAME
                : STAR_PLATINUM_BLINK_HEAVY_NAME;
    }

    private static boolean hasBlinkModifier(
            @Nullable EntityActionInstance action) {
        return action != null && action.getPunchModifiers().stream()
                .anyMatch(modifier -> modifier.effectType
                                == ReworkedAddon.STAR_PLATINUM_BLINK_MODIFIER
                                        .get()
                        || modifier.effectType
                                == ReworkedAddon
                                        .STAR_PLATINUM_UPPERCUT_BLINK_MODIFIER
                                        .get());
    }

    // 1.16 StarPlatinumBlinkPunch, a StandEntityActionModifier of the
    // Stand's running task (the heavy punch and the uppercut included): right
    // after the task's own perform, its strike too, it blinked and struck
    // once more with the punch of its heavy attack.
    public static final class StarPlatinumBlinkModifier
            extends StandEffectInstance {
        public StarPlatinumBlinkModifier(EntityCustomEffectType<?> type) {
            super(type);
            isFromStandAction = true;
        }

        @Override
        protected void start() {}

        // the task it modified ended or was replaced before its perform
        @Override
        protected void tick() {
            if (!level.isClientSide()
                    && (standAction == null || standAction.isOver())) {
                remove();
            }
        }

        @Override
        protected void stop() {}

        // 1.16 standPerform :120-124: the blink by the task's target (what
        // its own strike just took), then the task took what the ray found if
        // it kept it, by the range from where the Stand stood, and the strike
        // went to whatever the task held
        @Override
        public void onModifiedActionTick(EntityActionInstance action) {
            if (level.isClientSide()
                    || action.getPhase() != ActionPhase.PERFORM
                    || action.getPhaseTick() >= 1
                    || !(action.getPerformer() instanceof StandEntity stand)) {
                return;
            }
            remove();
            String blinkName = effectType
                            == ReworkedAddon.STAR_PLATINUM_UPPERCUT_BLINK_MODIFIER
                                    .get()
                    ? STAR_PLATINUM_BLINK_UPPERCUT_NAME
                    : STAR_PLATINUM_BLINK_HEAVY_NAME;
            ActionTarget rayTarget = ReworkedTimeSkip.blinkBeforePunch(
                    stand, blinkName, action.getTaskTarget(level));
            action.setTaskTarget(rayTarget);
            ActionTarget target = action.getTaskTarget(level);
            if (target.getType() == ActionTarget.TargetType.ENTITY) {
                action.standRotationTarget = target;
            }
            // the Stand strikes from where it stood: a blinked user's Stand followed on its next tick (1.16 :121-125)
            strikeAfterBlink(stand, blinkName, target);
        }
    }

    public static final class ReworkedTheWorldTSPunchAbility
            extends TheWorldTSPunchAbility {
        public ReworkedTheWorldTSPunchAbility(
                AbilityType<?> type, AbilityId id) {
            super(type, id);
        }

        // 1.16 ReTheWorldTSHeavyAttack.isUnlocked (:122-127) asked the stored
        // Resolve level (3) itself, then RE_THE_WORLD_TIME_STOP's unlock. Asked
        // as the Resolve check, so the ts_punch skill's own auto-unlock waits
        // for the time stop as well.
        @Override
        public ConditionCheck getResolveUnlockConditionCheck(
                Power<?> context) {
            ConditionCheck resolve =
                    super.getResolveUnlockConditionCheck(context);
            if (!resolve.isPositive()) {
                return resolve;
            }
            StandPower power = PowerClass.STAND.cast(context);
            if (power == null || !isResolveLevelHeld()
                    && power.getResolveLevel() < THE_WORLD_TS_PUNCH_RESOLVE_LEVEL) {
                return ConditionCheck.createNegative("not_unlocked");
            }
            Ability timeStop = power.getAbility(TimeStopLearning.TIME_STOP);
            return timeStop != null
                    ? timeStop.getUnlockConditionCheck(power)
                    : ConditionCheck.createNegative("not_unlocked");
        }

        // 1.16 ReTheWorldTSHeavyAttack.replaceAction: while the TS punch
        // performs or recovers, the press is the recovery blink, resolved
        // before any condition check so only the recovery's own apply.
        @Override
        public Ability replaceWithSubAbility(
                Power<?> context, AvailableAbilities abilities) {
            StandPower power = PowerClass.STAND.cast(context);
            if (power != null
                    && ReworkedTheWorldBlinkRecoveryAbility
                            .isEligibleRepeat(
                                    power.getSummonedStandEntity())) {
                Ability recovery = power.getAbility(
                        THE_WORLD_BLINK_RECOVERY_NAME);
                if (recovery != null) {
                    return recovery;
                }
            }
            return super.replaceWithSubAbility(context, abilities);
        }

        @Override
        public ResourceLocation getSpriteId(Power<?> context) {
            String fallbackSprite = super.getSpriteName(context);
            return configuredSprite(
                    "the_world_" + fallbackSprite,
                    CORE_NAMESPACE,
                    fallbackSprite);
        }
    }

    public static final class ReworkedTheWorldBlinkRecoveryAbility
            extends Ability {
        public ReworkedTheWorldBlinkRecoveryAbility(
                AbilityType<?> type, AbilityId id) {
            super(type, id);
            isSubAbility = true;
            partsRequired(StandPart.MAIN_BODY);
        }

        @Override
        public boolean addToControlSchemeEditing() {
            return false;
        }

        @Override
        public boolean shouldSetCooldownOnKeyPress(
                InputMethod inputMethod) {
            return false;
        }

        @Override
        public String getSpriteName(Power<?> context) {
            StandPower power = PowerClass.STAND.cast(context);
            return power != null
                            && ReworkedManualInput.isBlinkModifierHeld(
                                    power.getUser(),
                                    power.getSummonedStandEntity())
                    ? "the_world_ts_blink_recovery_self"
                    : "the_world_ts_blink_recovery";
        }

        @Override
        public ConditionCheck getUnlockConditionCheck(
                Power<?> context) {
            StandPower power = PowerClass.STAND.cast(context);
            // 1.16 TheWorldTSBlinkRecovery.isUnlocked asked getResolveLevel() >= 4 itself, so it
            // relocks when the stored level drops, unlike the learned unlocks of the core gate.
            if (power == null
                    || power.getResolveLevel() < 4) {
                return ConditionCheck.createNegative(
                        "not_unlocked");
            }
            Ability timeStop = power.getAbility("time_stop");
            return timeStop != null
                    ? timeStop.getUnlockConditionCheck(power)
                    : ConditionCheck.createNegative(
                            "not_unlocked");
        }

        @Override
        public ConditionCheck checkSpecificConditions(
                Power<?> context) {
            // 1.16 the_world_ts_blink_recovery did not ignore a stunned
            // performer (the summoned The World).
            ConditionCheck stun = checkPerformerStun(context);
            if (!stun.isPositive()) {
                return stun;
            }
            StandPower power = PowerClass.STAND.cast(context);
            StandEntity stand = power != null
                    ? power.getSummonedStandEntity()
                    : null;
            return power != null
                            && !power.isAbilityOnCooldown(
                                    TimeStopCooldowns.TIME_STOP_BLINK)
                            && isEligibleRepeat(stand)
                    ? ConditionCheck.POSITIVE
                    : ConditionCheck.NEGATIVE;
        }

        @Override
        public HeldInput onKeyPress(
                Level level,
                LivingEntity user,
                FriendlyByteBuf extraClientInput,
                InputMethod inputMethod,
                float clickHoldResolveTime,
                BufferingState bufferingState) {
            bufferingState.isActionSuccess = false;
            if (level instanceof net.minecraft.server.level.ServerLevel
                    serverLevel) {
                StandPower power = StandPower.get(user);
                StandEntity stand = power != null
                        ? power.getSummonedStandEntity()
                        : null;
                if (isEligibleRepeat(stand)) {
                    bufferingState.isActionSuccess =
                            ReworkedTimeSkip
                                    .performTheWorldRecovery(
                                            serverLevel,
                                            user,
                                            power,
                                            stand);
                }
            }
            return null;
        }

        private static boolean isEligibleRepeat(
                StandEntity stand) {
            if (stand == null || stand.getUser() == null
                    || ReworkedTimeSkip.isTimeStopped(
                            stand.level(), stand.getUser())) {
                return false;
            }
            EntityActionInstance action =
                    stand.getCurStandAction();
            if (action == null || action.ability == null) {
                return false;
            }
            ActionPhase phase = action.getPhase();
            return (phase == ActionPhase.PERFORM
                    || phase == ActionPhase.RECOVERY)
                    && THE_WORLD_TS_PUNCH_NAME.equals(
                            action.ability.getAbilityId()
                                    .nameInMoveset());
        }
    }

    // 1.16 RE_MAGICIANS_RED_PUNCH isTrained() (025 init :97), at most the
    // default 1 point; its full training unlocked the flame burst.
    public static final class ReworkedMagiciansRedPunchAbility
            extends StandEntityPunchAbility implements TrainableAbility {
        public ReworkedMagiciansRedPunchAbility(
                AbilityType<?> type, AbilityId id) {
            super(type, id, ReworkedMagiciansRedPunch::new);
            punchImpactSound(
                    ModSoundEvents.MAGICIANS_RED_PUNCH_LIGHT);
            // 1.16 ReMagiciansRedLightAttack is built from a plain
            // StandEntityLightAttack.Builder; the replacement does not get
            // the core punch customizer that sets the detached counter.
            detachedLightCounterKnockback();
        }

        @Override
        public String getLearningAbilityName() {
            return name();
        }

        @Override
        public float getMaxTrainingPoints(StandPower power) {
            return MAGICIANS_RED_PUNCH_MAX_TRAINING;
        }

        // 1.16 ReMagiciansRedLightAttack.onMaxTraining (:57-58)
        @Override
        public void onMaxTraining(StandPower power) {
            ReworkedAddon.MAGICIANS_RED_TRAINING_UNLOCKS.forceUnlock(
                    power, MAGICIANS_RED_FLAME_BURST_SKILL);
        }
    }

    // 1.16 ReMagiciansRedLightAttack.standPerform (:48-55): a punch whose
    // task target was living added Slow Burn and trained the punch by a 40th
    // of its maximum, whether the hurt went through or not.
    public static final class ReworkedMagiciansRedPunch
            extends StandEntityPunchAbility.StandEntityPunch {
        public ReworkedMagiciansRedPunch(EntityActionType ability) {
            super(ability);
        }

        @Override
        protected void hitEntity(
                ActionTarget target, Level level, StandEntity stand) {
            super.hitEntity(target, level, stand);
            if (target.getMainEntity() instanceof LivingEntity living) {
                stackSlowBurn(living, 0, 1);
            }
            StandPower power = StandPower.get(getPowerUser());
            if (target.getMainEntity() instanceof LivingEntity
                    && power != null && power.getCurTypeData() != null
                    && ability instanceof ReworkedMagiciansRedPunchAbility
                            punch) {
                float max = punch.getMaxTrainingPoints(power);
                power.getCurTypeData().addAbilityLearningProgressPoints(
                        punch.getLearningAbilityName(),
                        max / MAGICIANS_RED_PUNCH_HITS_TO_TRAIN, max, power);
            }
        }
    }

    public static final class ReworkedMagiciansRedKickAbility
            extends MagiciansRedKickAbility {
        public ReworkedMagiciansRedKickAbility(
                AbilityType<?> type, AbilityId id) {
            super(type, id);
            createActionObj = ReworkedMagiciansRedKick::new;
            heavyPunchImpactSound(
                    ModSoundEvents.MAGICIANS_RED_KICK_HEAVY);
            initIsFinisher("heavy_punch");
            // 1.16 RE_MAGICIANS_RED_KICK set no level (025 init :98), so
            // the finisher and its meter unlocked at Resolve 0, unlike the
            // core kick's 1.
            resolveLevelToUnlock(0);
            // 1.16 ReMagiciansRedKick: a plain StandEntityHeavyAttack.Builder,
            // as the core kick customizer sets it, with the recovery read
            // when it starts (getStandRecoveryTicks).
            setDefaultPhaseLength(ActionPhase.PERFORM, 1);
            detachedPunchKnockback();
            recoveryPhaseEntryTiming();
        }
    }

    // 1.16 ReMagiciansRedKick.standPerform (:51-67): after the kick, a living
    // task target's Slow Burn went off, or it was primed, hurt or not.
    public static final class ReworkedMagiciansRedKick
            extends MagiciansRedKickAbility.MagiciansRedKick {
        public ReworkedMagiciansRedKick(EntityActionType ability) {
            super(ability);
        }

        @Override
        protected void hitEntity(ActionTarget target, Level level,
                StandEntity stand, DamageSource dmgSource, float dmgAmount,
                float explRadius) {
            super.hitEntity(target, level, stand,
                    dmgSource, dmgAmount, explRadius);
            if (target.getMainEntity() instanceof LivingEntity living) {
                detonateOrPrimeSlowBurn(living);
            }
        }
    }

    public static final class ReworkedMagiciansRedHeavyPunchAbility
            extends StandEntityHeavyPunchAbility {
        public ReworkedMagiciansRedHeavyPunchAbility(
                AbilityType<?> type, AbilityId id) {
            super(type, id);
            createActionObj = ReworkedMagiciansRedHeavyPunch::new;
            heavyPunchImpactSound(
                    ModSoundEvents.MAGICIANS_RED_PUNCH_HEAVY);
            // 1.16 ReMagiciansRedHeavyPunch: a plain
            // StandEntityHeavyAttack.Builder, as the core heavy_punch
            // customizer sets it, with the recovery read when it starts.
            setDefaultPhaseLength(ActionPhase.PERFORM, 1);
            detachedPunchKnockback();
            recoveryPhaseEntryTiming();
        }
    }

    // 1.16 ReMagiciansRedHeavyPunch.standPerform (:47-54): after the strike,
    // a living task target's Slow Burn went up by 4, hurt or not.
    public static final class ReworkedMagiciansRedHeavyPunch
            extends StandEntityHeavyPunchAbility.StandEntityHeavyPunch {
        public ReworkedMagiciansRedHeavyPunch(EntityActionType ability) {
            super(ability);
        }

        @Override
        protected void hitEntity(ActionTarget target, Level level,
                StandEntity stand, DamageSource dmgSource, float dmgAmount,
                float explRadius) {
            super.hitEntity(target, level, stand,
                    dmgSource, dmgAmount, explRadius);
            if (target.getMainEntity() instanceof LivingEntity living) {
                stackSlowBurn(living, 4, 4);
            }
        }
    }

    // Only the Reworked punch, heavy punch and kick build Slow Burn. Current
    // charged-heavy, grab, barrage, and specials are deliberately outside the
    // legacy replacement surface.
    // 1.16 gave a target without Slow Burn amplifier 0 from the punch
    // (ReMagiciansRedLightAttack :52) but 4 from the heavy punch
    // (ReMagiciansRedHeavyPunch :51), not one less than the increment.
    private static void stackSlowBurn(
            LivingEntity target, int firstAmplifier, int increment) {
        MobEffectInstance current =
                target.getEffect(ReworkedAddon.SLOW_BURN);
        int amplifier = current == null
                ? firstAmplifier
                : current.getAmplifier() + increment;
        target.addEffect(new MobEffectInstance(
                ReworkedAddon.SLOW_BURN,
                200,
                amplifier,
                false,
                false,
                true));
    }

    private static void detonateOrPrimeSlowBurn(
            LivingEntity target) {
        MobEffectInstance current =
                target.getEffect(ReworkedAddon.SLOW_BURN);
        if (current == null) {
            target.addEffect(new MobEffectInstance(
                    ReworkedAddon.SLOW_BURN,
                    300,
                    6,
                    false,
                    false,
                    true));
            return;
        }

        int amplifier = current.getAmplifier();
        float burnTickDamage = amplifier < 6
                ? 1.0F / (amplifier + 1)
                : amplifier > 6
                        ? amplifier - 6
                        : 1.0F;
        target.hurt(
                target.damageSources().onFire(),
                current.getDuration()
                        / 20.0F
                        * burnTickDamage);
        target.clearFire();
        target.removeEffect(ReworkedAddon.SLOW_BURN);
    }

    // The core's flame burst shot already stands where 1.16 Reworked put it
    // (standOffsetFront(), turned by the view yaw)
    public static final class ReworkedMagiciansRedFlameBurstAbility
            extends MagiciansRedFlameBurstAbility {
        public ReworkedMagiciansRedFlameBurstAbility(
                AbilityType<?> type, AbilityId id) {
            super(type, id);
        }

        // 1.16 RE_MAGICIANS_RED_FLAME_BURST had noResolveUnlock() (025 init
        // :100): no Resolve level unlocked it, only the fully trained Reworked
        // punch. The core's starting skill gives the ability no lock of its
        // own, so the hidden skill is asked here.
        @Override
        public ConditionCheck getResolveUnlockConditionCheck(
                Power<?> context) {
            StandPower power = PowerClass.STAND.cast(context);
            return power != null && power.getCurTypeData() != null
                            && power.getCurTypeData().isSkillUnlocked(
                                    MAGICIANS_RED_FLAME_BURST_SKILL)
                    ? ConditionCheck.POSITIVE
                    : ConditionCheck.createNegative("not_unlocked");
        }
    }

    // 1.16 StandType.unlockNewActions unlocked a noResolveUnlock action for
    // a creative user too, for good; the core does that for its own training
    // unlocks on the user's tick (StandUnlockableSkill.unlockedByTraining).
    // The moveset is asked, not Power.getAbility: that warns for every
    // Stand without a flame burst, on each creative player's tick.
    public static void unlockFlameBurstForCreative(@Nullable StandPower power) {
        if (power != null && power.hasPower() && power.isUserCreative()
                && power.getCurTypeData() != null
                && power.getMoveset().getAbility("flame_burst")
                        instanceof ReworkedMagiciansRedFlameBurstAbility
                && !power.getCurTypeData().isSkillUnlocked(
                        MAGICIANS_RED_FLAME_BURST_SKILL)) {
            ReworkedAddon.MAGICIANS_RED_TRAINING_UNLOCKS.forceUnlock(
                    power, MAGICIANS_RED_FLAME_BURST_SKILL);
        }
    }

    // 1.16 StandEntity.attackTarget of the blink's target with the heavy
    // attack the blink was built with (RE_STAR_PLATINUM_HEAVY_PUNCH, or
    // RE_STAR_PLATINUM_UPPERCUT for the uppercut blink), from the modified
    // task, so with no heavy task of its own: no stamina, no finisher meter,
    // hand or recovery, the knockback of the Stand's last heavy finisher
    // value. The punch is an action of the blink ability that is never set
    // on the Stand and has no phase.
    static void strikeAfterBlink(
            StandEntity stand, String blinkName, ActionTarget blinkTarget) {
        LivingEntity user = stand.getUser();
        StandPower power = user != null ? StandPower.get(user) : null;
        if (power != null
                && power.getAbility(blinkName)
                        instanceof ReworkedStarPlatinumBlinkPunchAbility blink
                && blink.createActionObj() instanceof BlinkStrike strike) {
            strike.strikeAfterBlink(stand, user, blinkTarget);
        }
    }

    private interface BlinkStrike {
        void strikeAfterBlink(
                StandEntity stand, LivingEntity user, ActionTarget blinkTarget);
    }

    // The hit of StandEntityHeavyPunch.actionPerformStart; gives what an
    // entity hit left impactSoundDue at (null: the impact sounds).
    private interface HeavyHit {
        @Nullable
        Boolean hit(ActionTarget target, Level level, StandEntity stand,
                DamageSource dmgSource, float dmgAmount, float explRadius);
    }

    // StandEntityHeavyPunch.actionPerformStart's hit and impact sound
    private static void strikeBlinkTarget(
            EntityActionInstance punch,
            StandEntity stand,
            ActionTarget target,
            HeavyHit hit,
            Holder<SoundEvent> impactSound) {
        Level level = stand.level();
        stand.turnTowardsStrike(StandEntityPunchAbility.strikeAim(
                stand, target, StandEntityPunchAbility.CrosshairRule.ENTITY));
        Vec3 impactSoundPos = target.isEmpty(level)
                ? null
                : target.getCenterPos();
        DamageSource dmgSource = punch.makePunchDamageSource();
        ((DamageSourceModified) dmgSource)
                .jojo_ripples$setStandInvulTicks(10);
        float dmgAmount = StandStatFormulas.getHeavyAttackDamage(
                stand.getAttackDamage());
        float explRadius =
                StandEntityHeavyPunchAbility.calcExplosionRadius(stand);
        boolean deflected = StandEntityPunchAbility
                .deflectSilverChariotProjectiles(stand, target);
        Boolean impactSoundDue = null;
        if (target.getType() == ActionTarget.TargetType.BLOCK
                || target.getType() == ActionTarget.TargetType.ENTITY
                        && !deflected) {
            impactSoundDue = hit.hit(target, level, stand,
                    dmgSource, dmgAmount, explRadius);
        }
        if (impactSoundPos != null
                && (target.getType() == ActionTarget.TargetType.BLOCK
                        || target.getType() == ActionTarget.TargetType.ENTITY
                                && (impactSoundDue == null
                                        || impactSoundDue))) {
            StandUtil.broadcastSound((ServerLevel) level, impactSoundPos,
                    impactSound, true, StandPower.get(stand.getUser()),
                    stand.getSoundSource(), 1, 1);
        }
    }

    // The punch of the heavy blink: RE_STAR_PLATINUM_HEAVY_PUNCH's
    public static class BlinkHeavyPunch
            extends StandEntityHeavyPunchAbility.StandEntityHeavyPunch
            implements BlinkStrike {
        @Nullable
        private ActionTarget blinkTarget;

        public BlinkHeavyPunch(EntityActionType ability) {
            super(ability);
        }

        @Override
        protected ActionTarget getPunchTarget(StandEntity stand) {
            ActionTarget target = ReworkedTimeSkip.getBlinkPunchTarget(
                    stand, blinkTarget);
            return target != null ? target : super.getPunchTarget(stand);
        }

        @Override
        public void strikeAfterBlink(
                StandEntity stand, LivingEntity user, ActionTarget target) {
            performer = stand;
            powerUser.setEntity(user);
            finisherValue = stand.getLastHeavyFinisherValue();
            blinkTarget = target;
            setActionTargetSnapshot(target);
            ActionTarget struck = getPunchTarget(stand);
            strikeBlinkTarget(this, stand, struck, this::hitOnce,
                    getHeavyPunchImpactSound(struck));
        }

        @Nullable
        private Boolean hitOnce(ActionTarget target, Level level,
                StandEntity stand, DamageSource dmgSource, float dmgAmount,
                float explRadius) {
            impactSoundDue = null;
            if (target.getType() == ActionTarget.TargetType.ENTITY) {
                hitEntity(target, level, stand,
                        dmgSource, dmgAmount, explRadius);
            }
            else {
                hitBlock(target, level, stand,
                        dmgSource, dmgAmount, explRadius);
            }
            return impactSoundDue;
        }
    }

    // The punch of the uppercut blink: RE_STAR_PLATINUM_UPPERCUT's
    public static final class BlinkUppercut
            extends HeavyPunchUppercutAbility.Uppercut
            implements BlinkStrike {
        @Nullable
        private ActionTarget blinkTarget;

        public BlinkUppercut(EntityActionType ability) {
            super(ability);
        }

        @Override
        protected ActionTarget getPunchTarget(StandEntity stand) {
            ActionTarget target = ReworkedTimeSkip.getBlinkPunchTarget(
                    stand, blinkTarget);
            return target != null ? target : super.getPunchTarget(stand);
        }

        @Override
        public void strikeAfterBlink(
                StandEntity stand, LivingEntity user, ActionTarget target) {
            performer = stand;
            powerUser.setEntity(user);
            finisherValue = stand.getLastHeavyFinisherValue();
            blinkTarget = target;
            setActionTargetSnapshot(target);
            ActionTarget struck = getPunchTarget(stand);
            strikeBlinkTarget(this, stand, struck, this::hitOnce,
                    getHeavyPunchImpactSound(struck));
        }

        @Nullable
        private Boolean hitOnce(ActionTarget target, Level level,
                StandEntity stand, DamageSource dmgSource, float dmgAmount,
                float explRadius) {
            impactSoundDue = null;
            if (target.getType() == ActionTarget.TargetType.ENTITY) {
                hitEntity(target, level, stand,
                        dmgSource, dmgAmount, explRadius);
            }
            else {
                hitBlock(target, level, stand,
                        dmgSource, dmgAmount, explRadius);
            }
            return impactSoundDue;
        }
    }
}
