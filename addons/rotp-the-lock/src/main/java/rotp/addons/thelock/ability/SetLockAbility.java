package rotp.addons.thelock.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.subsystems.target.ActionTargetRange;

import rotp.addons.thelock.TheLockAddon;
import rotp.addons.thelock.state.LockStateService;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

public final class SetLockAbility
        extends EntityActionAbility {
    public static final int HOLD_TO_FIRE_TICKS = 10;
    public static final float STAMINA_COST = 100.0F;

    public SetLockAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, SetLockAction::new);
        setDefaultPhaseLength(
                ActionPhase.WINDUP, HOLD_TO_FIRE_TICKS);
        // 1.16 StandAction.getPerformer: the stun that counts is the
        // summoned Lock's, else the user's (checkPerformerStun).
        setIgnoresPerformerStun();
    }

    @Override
    protected float getWindupHoldToFireIndicatorLength() {
        return HOLD_TO_FIRE_TICKS;
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
        return null;
    }

    // 1.16 holdToFire(10, false) held the lock until it fired, so a
    // release from any bind still cancels the windup.
    @Override
    public boolean isActionHeld(EntityActionInstance action) {
        return action.getPhase() == ActionPhase.WINDUP
                || super.isActionHeld(action);
    }

    // 1.16's client sent no press of a held action while the power held
    // another (ActionsOverlayGui.onActionClick): the press waits in the
    // input buffer until that hold ends.
    @Override
    public boolean isHoldAction() {
        return true;
    }

    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return false;
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck stun = checkPerformerStun(context);
        if (!stun.isPositive()) {
            return stun;
        }
        ConditionCheck check = super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        LivingEntity user = TheLockTargeting.owningUser(context);
        if (user == null) {
            return ConditionCheck.NEGATIVE;
        }
        return checkLiveTarget(user, true);
    }

    // 1.16 PowerBaseImpl.tickHeldAction reran checkRequirements on the
    // crosshair every held tick: no living or an already locked target
    // stopped the hold silently, a target past the range with
    // target_too_far.
    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action, Power<?> context) {
        ConditionCheck stun = checkPerformerStun(context);
        if (!stun.isPositive()) {
            return stun;
        }
        LivingEntity user = TheLockTargeting.owningUser(context);
        if (user == null) {
            return ConditionCheck.NEGATIVE;
        }
        return checkLiveTarget(user, false);
    }

    @Override
    public boolean canContinueAction(EntityActionInstance action) {
        if (!super.canContinueAction(action)) {
            return false;
        }
        if (action.getPhase() != ActionPhase.WINDUP) {
            return true;
        }
        LivingEntity user = action.getPowerUser();
        return user != null
                && TheLockAddon.isTheLockUser(user)
                && (AbilityInput.isHeldByKey(user, action)
                        || checkLiveTarget(user, false).isPositive());
    }

    // 1.16 order: Action.checkRangeAndTarget (8 blocks from the performer,
    // 4 without a line of sight), then TheLockSetLock.checkTarget.
    private static ConditionCheck checkLiveTarget(
            LivingEntity user, boolean pressMessages) {
        LivingEntity raw = TheLockTargeting.raytraceRawTarget(user);
        LivingEntity target = TheLockTargeting.stateTarget(raw);
        if (target == null || !target.isAlive()) {
            return pressMessages
                    ? ConditionCheck.createNegative(Component.translatable(
                            "rotp_tl.message.no_target"))
                    : ConditionCheck.NEGATIVE;
        }
        if (!ActionTargetRange.isEntityWithinRange(
                ActionTargetRange.standPerformer(user), raw,
                ActionTargetRange.DEFAULT_ENTITY_RANGE_SQ)) {
            return ConditionCheck.createNegative("target_too_far");
        }
        if ((target.hasData(TheLockAddon.LOCK_STATE)
                && TheLockAddon.lockState(target).active())
                || target.hasEffect(TheLockAddon.LOCK_EFFECT)) {
            return pressMessages
                    ? ConditionCheck.createNegative(Component.translatable(
                            "rotp_tl.message.target_locked"))
                    : ConditionCheck.NEGATIVE;
        }
        return ConditionCheck.POSITIVE;
    }

    public static final class SetLockAction
            extends EntityActionInstance {
        public SetLockAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onButtonStopHold() {
            if (getPhase() == ActionPhase.WINDUP) {
                forceStop();
            }
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user == null || !TheLockAddon.isTheLockUser(user)) {
                return;
            }
            // 1.16 stopHeldAction(true) checked the requirements once
            // more on the crosshair; a failure fired nothing, silently.
            StandPower power = StandPower.get(user);
            if (!(ability instanceof SetLockAbility setLock)
                    || power == null
                    || !setLock.checkHeldActionConditions(this, power)
                            .isPositive()) {
                return;
            }
            LivingEntity target =
                    TheLockTargeting.raytraceStateTarget(user);

            // 1.16 StandAction.onPerform paid first with the result
            // ignored; a short bar just drops to 0.
            StandAbilityStamina.consume(
                    ability,
                    StandPower.get(user),
                    STAMINA_COST,
                    false);
            LockStateService.lock(target, user);
        }
    }
}
