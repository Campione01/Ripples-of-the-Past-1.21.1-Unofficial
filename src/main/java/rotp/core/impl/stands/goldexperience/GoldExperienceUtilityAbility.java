package rotp.core.impl.stands.goldexperience;

import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;

public abstract class GoldExperienceUtilityAbility extends Ability {

    protected GoldExperienceUtilityAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
    }
}
