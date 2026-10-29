package rotp.addons.stickyfingers.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.subsystems.entity_possessionv2.LivingComponentPossession;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.subsystems.target.ActionTargetRange;

import rotp.addons.stickyfingers.StickyFingersPhase1Spec;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;

public final class StickyFingersHideInMobAbility
        extends StandEntityAbility {
    public static final String POSSESSION_TYPE =
            "sticky_fingers_hide_in_mob";
    private static final int HOLD_TO_FIRE_TICKS = 30;

    public StickyFingersHideInMobAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, HideInMobAction::new);
        setButtonHoldPhase(ActionPhase.BUTTON_CHARGE);
        setDefaultPhaseLength(
                ActionPhase.BUTTON_CHARGE, HOLD_TO_FIRE_TICKS);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
    }

    // 1.16 sticky_fingers_hide_in_mob was a StandAction its user's power
    // performed in the hold's 30th tick, not a task of the Stand.
    @Override
    public boolean chargeFiresAsItsLengthEnds(EntityActionInstance action) {
        return true;
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck check = super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        LivingEntity user = context.getUser();
        return user != null
                        && getValidTarget(user) != null
                ? ConditionCheck.POSITIVE
                : ConditionCheck.createNegative(
                        "sticky_fingers_hide_target");
    }

    // 1.16 tickHeldAction re-ran checkRequirements on the crosshair
    // (TargetRequirement.ENTITY, checkTarget): the charge stopped silently
    // once it left a mob the hide takes, and with target_too_far past
    // Action.checkRangeAndTarget's 8 blocks of the performer (4 unseen).
    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action, Power<?> context) {
        ConditionCheck base =
                super.checkHeldSpecificConditions(action, context);
        if (!base.isPositive()) {
            return base;
        }
        LivingEntity user = context.getUser();
        LivingEntity target = user != null
                ? getValidTarget(user)
                : null;
        if (target == null) {
            return ConditionCheck.NEGATIVE;
        }
        return ActionTargetRange.isEntityWithinRange(
                        ActionTargetRange.standPerformer(user),
                        target,
                        ActionTargetRange.DEFAULT_ENTITY_RANGE_SQ)
                ? ConditionCheck.POSITIVE
                : ConditionCheck.createNegative("target_too_far");
    }

    // The press check also runs on the user's client (the HUD, the client's
    // own press check), whose copy of the user holds no stored aim (1.16:
    // the mouse target).
    private static LivingEntity getValidTarget(LivingEntity user) {
        ActionTarget target =
                LivingComponentAction.getAimTarget(user);
        if (target.getType() != TargetType.ENTITY
                || !(target.getMainEntity()
                        instanceof LivingEntity livingTarget)
                || livingTarget.is(user)
                || livingTarget instanceof StandEntity
                || !livingTarget.isAlive()
                || user.distanceToSqr(livingTarget)
                        > StickyFingersPhase1Spec
                                .HIDE_IN_MOB_RANGE
                                * StickyFingersPhase1Spec
                                        .HIDE_IN_MOB_RANGE) {
            return null;
        }
        EntityDimensions userSize =
                user.getDimensions(Pose.STANDING);
        EntityDimensions targetSize =
                livingTarget.getDimensions(Pose.STANDING);
        float userVolume =
                userSize.width() * userSize.width()
                        * userSize.height() * 0.9F;
        float targetVolume =
                targetSize.width() * targetSize.width()
                        * targetSize.height();
        return targetVolume <= userVolume ? livingTarget : null;
    }

    public static final class HideInMobAction
            extends EntityActionInstance {
        public HideInMobAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onButtonStopHold() {
            if (getPhase() == ActionPhase.BUTTON_CHARGE
                    && getPhaseTick() < HOLD_TO_FIRE_TICKS) {
                forceStop();
                syncPhaseChanges();
            }
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()) {
                return;
            }
            LivingEntity user = getPowerUser();
            LivingEntity target =
                    user != null
                            ? getValidTarget(user)
                            : null;
            if (user != null && target != null) {
                LivingComponentPossession.setPossessionTarget(
                        user, target, POSSESSION_TYPE);
            }
        }
    }
}
