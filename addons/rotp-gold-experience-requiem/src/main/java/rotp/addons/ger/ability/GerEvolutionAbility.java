package rotp.addons.ger.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;

/**
 * Registry identity for the legacy action. The reachable trigger is the
 * server-owned Beetle Arrow interaction while Gold Experience is equipped.
 */
public final class GerEvolutionAbility extends StandEntityAbility {
    public GerEvolutionAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, EvolutionNoticeAction::new);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        return base.isPositive()
                ? ConditionCheck.createNegative("already_requiem")
                : base;
    }

    public static final class EvolutionNoticeAction
            extends EntityActionInstance {
        public EvolutionNoticeAction(EntityActionType ability) {
            super(ability);
        }
    }
}
