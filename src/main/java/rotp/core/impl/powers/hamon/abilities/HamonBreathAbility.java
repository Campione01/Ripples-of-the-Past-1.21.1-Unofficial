package rotp.core.impl.powers.hamon.abilities;

import rotp.core.client.sound.ClientsideSoundsHelper;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;

import net.minecraft.world.entity.LivingEntity;

public class HamonBreathAbility extends HamonActionRuntimeAbility {

	public HamonBreathAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, BreathInstance::new);
		setDefaultPhaseLength(ActionPhase.WINDUP, 0);
		setButtonHoldPhase(ActionPhase.PERFORM);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
		// 1.16 HamonBreath#playVoiceLine overrode the sneak rule away
		setPlaysVoiceLineOnSneak();
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

	// 1.16 HamonBreath.checkSpecificConditions ran on every held tick too: losing air ends the breath.
	@Override
	protected ConditionCheck checkHeldSpecificConditions(EntityActionInstance action, Power<?> context) {
		ConditionCheck check = super.checkHeldSpecificConditions(action, context);
		if (!check.isPositive()) {
			return check;
		}
		LivingEntity user = context.getUser();
		return user != null && user.getAirSupply() >= user.getMaxAirSupply()
				? ConditionCheck.POSITIVE : ConditionCheck.createNegative("no_air");
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
