package rotp.addons.purplehaze.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;

import rotp.addons.purplehaze.entity.PurpleHazeEntity;

public final class PurpleHazeMadnessBarrageAbility
        extends StandEntityBarrageAbility {
    public PurpleHazeMadnessBarrageAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        createActionObj = MadnessBarrageAction::new;
        setDefaultPhaseLength(ActionPhase.PERFORM, 60);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 2);
        standAutoSummonMode(AutoSummonMode.DISABLED);
    }

    @Override
    protected ConditionCheck checkStandEntityConditions(
            StandPower standPower, StandEntity standEntity) {
        return ConditionCheck.createNegative(
                "cant_control_stand");
    }

    @Override
    public boolean retractsStandAfterAction(
            StandPower standPower,
            StandEntity standEntity,
            EntityActionInstance action) {
        return false;
    }

    public static final class MadnessBarrageAction
            extends StandEntityBarrage {
        public MadnessBarrageAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onSetPhase(ActionPhase newPhase) {
            super.onSetPhase(newPhase);
            userWalkSpeed = 1.0F;
        }
    }
}
