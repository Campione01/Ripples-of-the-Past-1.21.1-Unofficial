package com.github.standobyte.jojoimpl.powers.zombie.abilities;

import java.util.function.Function;

import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.EntityActionAbility;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojoimpl.powers.zombie.ZombieData;

import net.minecraft.world.entity.LivingEntity;

public class ZombieActionAbility extends EntityActionAbility {
	private final boolean blockedByDisguise;
	private final float energyCost;

	public ZombieActionAbility(AbilityType<?> abilityType, AbilityId abilityId,
			boolean blockedByDisguise, float energyCost,
			Function<EntityActionType, ? extends EntityActionInstance> createActionObj) {
		super(abilityType, abilityId, createActionObj);
		this.blockedByDisguise = blockedByDisguise;
		this.energyCost = energyCost;
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		ConditionCheck check = super.checkSpecificConditions(context);
		if (!check.isPositive()) {
			return check;
		}
		ZombieData zombie = getZombieData(context);
		if (zombie == null) {
			return ConditionCheck.NEGATIVE;
		}
		if (blockedByDisguise && zombie.isDisguiseEnabled()) {
			return ConditionCheck.createNegative("disguise");
		}
		if (energyCost > 0.0F && !zombie.hasEnergy(energyCost)) {
			return ConditionCheck.createNegative("no_energy_zombie");
		}
		return ConditionCheck.POSITIVE;
	}

	protected float getEnergyCost() {
		return energyCost;
	}

	protected static ZombieData getZombieData(Power<?> context) {
		return context != null && context.getCurTypeData() instanceof ZombieData zombie ? zombie : null;
	}

	protected static ZombieData getZombieData(LivingEntity user, EntityActionInstance action) {
		return action != null ? getZombieData(action.ability instanceof ZombieActionAbility ability
				? ability.getUserPower(user) : null) : null;
	}
}
