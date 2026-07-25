package com.github.standobyte.jojoimpl.powers.hamon.abilities;

import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.Ability;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;

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
