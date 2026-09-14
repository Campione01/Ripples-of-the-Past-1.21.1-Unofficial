package rotp.core.impl.powers.hamon.abilities;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;

public class HamonLiquidWalkingAbility extends Ability {

	public HamonLiquidWalkingAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId);
	}

	@Override
	public boolean addToControlSchemeEditing() {
		return false;
	}

	@Override
	public boolean isAbilityAvailable(Power<?> context) {
		return false;
	}
}
