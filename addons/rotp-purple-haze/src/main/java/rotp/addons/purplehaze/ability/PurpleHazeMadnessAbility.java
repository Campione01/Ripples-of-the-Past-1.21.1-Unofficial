package rotp.addons.purplehaze.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;

import rotp.addons.purplehaze.PurpleHazeAddon;
import rotp.addons.purplehaze.entity.PurpleHazeEntity;
import net.minecraft.world.entity.LivingEntity;

public final class PurpleHazeMadnessAbility
        extends StandEntityAbility {
    public PurpleHazeMadnessAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, MadnessAction::new);
        usageGroup = AbilityUsageGroup.UTILITY;
        // 1.16 holdToFire(40, false) without standRecoveryTicks
        setDefaultPhaseLength(ActionPhase.BUTTON_CHARGE, 40);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        standAutoSummonMode(AutoSummonMode.FULL);
        partsRequired(StandPart.MAIN_BODY);
    }

    @Override
    public ConditionCheck checkMainModLogicConditions(
            Power<?> context) {
        ConditionCheck parent =
                super.checkMainModLogicConditions(context);
        if (!parent.isPositive()
                && context instanceof StandPower power
                && power.isAbilityOnCooldown(name())
                && power.getSummonedStandEntity()
                        instanceof PurpleHazeEntity stand
                && (stand.isMadCauseOfAbility()
                        || isCurrentMadnessAction(stand))) {
            return ConditionCheck.POSITIVE;
        }
        return parent;
    }

    private boolean isCurrentMadnessAction(
            PurpleHazeEntity stand) {
        EntityActionInstance action = stand.getCurStandAction();
        return action != null
                && action.ability.getAbilityId() != null
                && action.ability.getAbilityId().equals(getAbilityId());
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck parent =
                super.checkSpecificConditions(context);
        if (!parent.isPositive()) {
            return parent;
        }
        return context instanceof StandPower power
                && power.getPowerType()
                        == PurpleHazeAddon.PURPLE_HAZE.get()
                ? ConditionCheck.POSITIVE
                : ConditionCheck.NEGATIVE;
    }

    @Override
    protected ConditionCheck checkStandEntityConditions(
            StandPower standPower, StandEntity standEntity) {
        ConditionCheck parent = super.checkStandEntityConditions(
                standPower, standEntity);
        if (!parent.isPositive()) {
            return parent;
        }
        return standEntity instanceof PurpleHazeEntity stand
                && (!stand.isMad() || stand.isMadCauseOfAbility())
                ? ConditionCheck.POSITIVE
                : ConditionCheck.createNegative("cant_control_stand");
    }

    // 1.16 holdToFire(40, false) cooldown(400): the cooldown starts when
    // the hold fires, so a cancelled hold leaves none.
    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return false;
    }

    @Override
    public boolean shouldBufferInput(
            LivingComponentAction performerAction) {
        return false;
    }

    @Override
    public boolean retractsStandAfterAction(
            StandPower standPower,
            StandEntity standEntity,
            EntityActionInstance action) {
        return false;
    }

    public static final class MadnessAction
            extends EntityActionInstance {
        public MadnessAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onActionSet(
                EntityActionInstance previousAction) {
            if (!level().isClientSide()
                    && getPerformer()
                            instanceof StandEntity stand) {
                StandUtil.playStandEntitySound(
                        stand,
                        PurpleHazeAddon.PH_MADNESS,
                        1.0F,
                        1.0F);
            }
            // 1.16 purple_haze_madness .shout(PURPLE_HAZE_MADNESS_FUGO)
            LivingEntity shouter = getPowerUser();
            if (!level().isClientSide() && shouter != null) {
                sayShoutOf(ability, shouter, PurpleHazeAddon.PH_FUGO_MADNESS);
            }
        }

        @Override
        public void actionPerformStart() {
            if (!level().isClientSide()
                    && getPerformer()
                            instanceof PurpleHazeEntity stand) {
                LivingEntity user = getPowerUser();
                if (user != null
                        && ability instanceof PurpleHazeMadnessAbility madness) {
                    madness.setCooldownOnUse(StandPower.get(user));
                }
                stand.toggleAbilityMadness();
                if (!stand.isMad()) {
                    LivingComponentAction.getComponent(stand)
                            .setAction(
                                    null,
                                    SyncType.TRACKING_AND_SELF);
                }
            }
        }

        @Override
        public void onButtonStopHold() {
            if (getPhase() == ActionPhase.BUTTON_CHARGE
                    && getPerformer() != null) {
                LivingComponentAction.getComponent(
                                getPerformer())
                        .setAction(
                                null,
                                SyncType.TRACKING_AND_SELF);
            }
        }
    }
}
