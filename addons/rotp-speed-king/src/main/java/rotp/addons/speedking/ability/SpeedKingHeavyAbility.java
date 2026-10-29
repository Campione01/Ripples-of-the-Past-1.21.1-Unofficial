package rotp.addons.speedking.ability;

import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.speedking.SpeedKingAddon;

/**
 * 1.16 099 InitStands :63-64: example_stand_heavy_punch and example_stand_finisher_punch were plain core
 * StandEntityHeavyAttacks (punchSound STAND_PUNCH_HEAVY, partsRequired ARMS), the finisher being the heavy punch's
 * setFinisherVariation: the core heavy punch, its damage from the Stand's strength, the finisher's free recovery.
 */
public final class SpeedKingHeavyAbility extends StandEntityHeavyPunchAbility {
    public static final String HEAVY_NAME = "example_stand_heavy_punch";

    public SpeedKingHeavyAbility(AbilityType<?> type, AbilityId id, boolean finisher) {
        super(type, id);
        // 1.16 StandEntityAction.Builder standPerformDuration 1
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        if (finisher) {
            // 1.16 setFinisherVariation: the heavy punch's key gives the
            // finisher from half a finisher meter; its own hotbar slot stays
            initIsFinisher(HEAVY_NAME);
        }
    }

    public static SpeedKingHeavyAbility heavy(AbilityType<?> type, AbilityId id) {
        return new SpeedKingHeavyAbility(type, id, false);
    }

    public static SpeedKingHeavyAbility finisher(AbilityType<?> type, AbilityId id) {
        return new SpeedKingHeavyAbility(type, id, true);
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        if (base.isPositive() && !(context instanceof StandPower power
                && SpeedKingAddon.isUser(power.getUser()))) {
            return ConditionCheck.NEGATIVE;
        }
        return base;
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
        return ActionAnimIdentifier.getOrCreate("heavy_punch", false);
    }
}
