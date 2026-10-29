package rotp.addons.speedwagon.ability;

import rotp.core.client.ClientProxy;
import rotp.core.client.input.ClientsideAim;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.subsystems.target.ActionTargetRange;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class HotAbsAbility
        extends SpeedwagonActionAbility {
    public static final int HOLD_TO_FIRE_TICKS = 60;

    public HotAbsAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(
                abilityType,
                abilityId,
                100.0F,
                0.0F,
                0,
                HotAbsAction::new);
        setButtonHoldPhase(ActionPhase.BUTTON_CHARGE);
        setDefaultPhaseLength(
                ActionPhase.BUTTON_CHARGE,
                HOLD_TO_FIRE_TICKS);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
    }

    // 1.16 PowerBaseImpl.checkRequirements: the crosshair target
    // (Skill3 ENTITY requirement, Action.checkRangeAndTarget) before
    // SpeedwagonAction and NonStandAction.checkConditions.
    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        LivingEntity user = context.getUser();
        ConditionCheck targetCheck = user != null
                ? checkAimedTarget(user)
                : ConditionCheck.NEGATIVE;
        if (!targetCheck.isPositive()) {
            return targetCheck;
        }
        return super.checkSpecificConditions(context);
    }

    // 1.16 tickHeldAction ran the same check on the crosshair every
    // held tick; a failure ended the charge before it fired.
    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action, Power<?> context) {
        return checkSpecificConditions(context);
    }

    @Override
    protected float getWindupHoldToFireIndicatorLength() {
        return HOLD_TO_FIRE_TICKS;
    }

    static ConditionCheck checkAimedTarget(LivingEntity user) {
        Entity target = aimedEntity(user);
        if (target == null || target == user) {
            return ConditionCheck.NEGATIVE;
        }
        if (!ActionTargetRange.isEntityWithinRange(user, target,
                ActionTargetRange.DEFAULT_ENTITY_RANGE_SQ)) {
            return ConditionCheck.createNegative("target_too_far");
        }
        return ConditionCheck.POSITIVE;
    }

    private static Entity aimedEntity(LivingEntity user) {
        ActionTarget target;
        // The user's aim only goes from its client to the server.
        if (user.level().isClientSide()
                && user == ClientProxy.getClientPlayer()) {
            target = ClientsideAim.playerAim.getTarget()
                    .resolveEntityId(user.level());
        }
        else {
            var aim = LivingComponentAction.getAim(user);
            target = aim != null
                    ? aim.getTarget().resolveEntityId(user.level())
                    : null;
        }
        return target != null
                && target.getType() == TargetType.ENTITY
                && target.getEntity() != null
                && !target.getEntity().isRemoved()
                ? target.getEntity()
                : null;
    }

    public static final class HotAbsAction
            extends EntityActionInstance {
        public HotAbsAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onActionSet(
                EntityActionInstance previousAction) {
            LivingEntity user = getPowerUser();
            if (user != null) {
                captureActionTargetFromAim(user);
            }
        }

        @Override
        public void onButtonStopHold() {
            if (getPhase() == ActionPhase.BUTTON_CHARGE
                    && getPhaseTick()
                            < HOLD_TO_FIRE_TICKS) {
                forceStop();
                syncPhaseChanges();
            }
        }

        // 1.16 stopHeldAction(true) checked the requirements once more
        // and performed on the entity under the crosshair at that moment.
        @Override
        public void actionPerformStart() {
            if (level().isClientSide()
                    || !(ability
                            instanceof HotAbsAbility hotAbsAbility)) {
                return;
            }
            LivingEntity user = getPowerUser();
            Power<?> context = user != null
                    ? hotAbsAbility.getUserPower(user)
                    : null;
            if (context == null
                    || !hotAbsAbility.checkHeldActionConditions(
                            this, context).isPositive()
                    || !hotAbsAbility.consumeInitialEnergy(user)) {
                forceStop();
                syncPhaseChanges();
                return;
            }
            if (aimedEntity(user) instanceof LivingEntity target) {
                target.removeEffect(
                        ModStatusEffects.FREEZE);
            }
        }
    }
}
