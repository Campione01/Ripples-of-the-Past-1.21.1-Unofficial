package com.github.standobyte.jojoimpl.stands.goldexperience;

import com.github.standobyte.jojo.powersystem.standpower.StandInstance.StandPart;

import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.entityaction.ActionAnimIdentifier;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;

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
