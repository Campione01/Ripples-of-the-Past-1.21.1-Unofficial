package rotp.addons.catchtherainbow.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;

/**
 * Compatibility registry alias. RainStopAbility delegates both stop and
 * manual resume to the core TimeStopAbility lifecycle.
 */
public final class RainResumeAbility extends Ability {
    public RainResumeAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
    }

    @Override
    public boolean isAbilityAvailable(Power<?> context) {
        return false;
    }

    @Override
    public boolean addToControlSchemeEditing() {
        return false;
    }
}
