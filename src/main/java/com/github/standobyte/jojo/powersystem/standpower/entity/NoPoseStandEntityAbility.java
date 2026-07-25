package com.github.standobyte.jojo.powersystem.standpower.entity;

import java.util.function.Function;

import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.entityaction.ActionAnimIdentifier;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;

public class NoPoseStandEntityAbility extends StandEntityAbility {
	private static final ActionAnimIdentifier IDLE_ANIM = ActionAnimIdentifier.getOrCreate("idle", true);

	public NoPoseStandEntityAbility(AbilityType<?> abilityType, AbilityId abilityId,
			Function<EntityActionType, ? extends EntityActionInstance> createActionObj) {
		super(abilityType, abilityId, createActionObj);
	}

	@Override
	public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
		return IDLE_ANIM;
	}
}
