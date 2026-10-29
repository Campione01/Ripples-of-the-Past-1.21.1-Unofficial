package rotp.addons.speedking.ability;

import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.speedking.SpeedKingAddon;

/**
 * 1.16 099 InitStands :62: example_stand_barrage was the plain core StandEntityMeleeBarrage (barrageHitSound
 * STAND_PUNCH_LIGHT, the default): hits a tick from the Stand's speed, damage from its strength, the finisher meter,
 * the clash, its hold limit, recovery and heavy follow-up, and the core's stamina rule (a tick it cannot pay ends it).
 */
public final class SpeedKingBarrageAbility extends StandEntityBarrageAbility {
    public SpeedKingBarrageAbility(AbilityType<?> type, AbilityId id) {
        super(type, id);
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
        return ActionAnimIdentifier.getOrCreate("barrage", false);
    }
}
