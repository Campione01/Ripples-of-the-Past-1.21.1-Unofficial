package rotp.addons.cmoon.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;

public final class CMoonBarrageAbility
        extends StandEntityBarrageAbility {
    private static final ActionAnimIdentifier ANIMATION =
            ActionAnimIdentifier.getOrCreate("barrage", false);

    public CMoonBarrageAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(
            EntityActionInstance action) {
        return ANIMATION;
    }
}
