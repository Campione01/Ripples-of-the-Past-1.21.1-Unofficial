package rotp.core.powersystem.standpower.entity;

import java.util.function.Function;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;

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
