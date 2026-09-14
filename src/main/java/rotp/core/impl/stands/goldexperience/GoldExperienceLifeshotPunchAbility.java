package rotp.core.impl.stands.goldexperience;

import rotp.core.powersystem.standpower.StandInstance.StandPart;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.EntityActionInstance;

public class GoldExperienceLifeshotPunchAbility extends GoldExperienceHeavyPunchAbility {
    private static final ActionAnimIdentifier FINISHER_ANIM = ActionAnimIdentifier.getOrCreate("finisher", false);

    public GoldExperienceLifeshotPunchAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        partsRequired(StandPart.ARMS);
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
        return FINISHER_ANIM;
    }
}
