package com.github.standobyte.jojoimpl.powers.hamon.abilities;

import com.github.standobyte.jojo.client.sound.ClientsideSoundsHelper;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;

import net.minecraft.world.entity.LivingEntity;

public class HamonBreathAbility extends HamonActionRuntimeAbility {

	public HamonBreathAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, BreathInstance::new);
		setDefaultPhaseLength(ActionPhase.WINDUP, 0);
		setButtonHoldPhase(ActionPhase.PERFORM);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		ConditionCheck check = super.checkSpecificConditions(context);
		if (!check.isPositive()) {
			return check;
		}
		if (context == null || !(context.getUser() instanceof LivingEntity user)) {
			return ConditionCheck.NEGATIVE;
		}
		return user.getAirSupply() >= user.getMaxAirSupply() ? ConditionCheck.POSITIVE : ConditionCheck.createNegative("no_air");
	}

	public static class BreathInstance extends HamonActionRuntimeAbility.HamonHeldActionInstance {
		public BreathInstance(EntityActionType ability) { super(ability); }

		@Override
		public void onSetPhase(ActionPhase newPhase) {
			super.onSetPhase(newPhase);
			if (newPhase == ActionPhase.PERFORM && level().isClientSide()) {
				LivingEntity user = getPowerUser();
				if (user != null) {
					ClientsideSoundsHelper.playLoopingActionSound(ModSoundEvents.HAMON_CONCENTRATION.get(), user, this,
							ActionPhase.PERFORM, 1.0F, 1.0F, 15);
				}
			}
		}
	}
}
