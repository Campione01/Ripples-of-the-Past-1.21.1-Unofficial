package com.github.standobyte.jojoimpl.stands.goldexperience;

import com.github.standobyte.jojo.powersystem.ability.Ability;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;

public abstract class GoldExperienceUtilityAbility extends Ability {

    protected GoldExperienceUtilityAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
    }
}
