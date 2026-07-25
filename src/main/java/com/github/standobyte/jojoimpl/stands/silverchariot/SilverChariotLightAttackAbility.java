package com.github.standobyte.jojoimpl.stands.silverchariot;

import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.ability.Ability;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.condition.AvailableAbilities;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.subsystems.target.ActionTarget;
import com.github.standobyte.jojoimpl.stands._entitybase.StandEntityPunchAbility;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;

public class SilverChariotLightAttackAbility extends StandEntityPunchAbility {
	private static final String NO_RAPIER_ABILITY = "no_rapier_light_attack";

	public SilverChariotLightAttackAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, SilverChariotLightAttack::new);
		spriteName = isNoRapierVariation() ? NO_RAPIER_ABILITY : "light_attack";
		if (isNoRapierVariation()) {
			isSubAbility = true;
		}
	}

	@Override
	public Ability replaceWithSubAbility(Power<?> context, AvailableAbilities abilities) {
		if (isNoRapierVariation()) {
			return this;
		}
		if (abilities != null && lacksRapier(context)) {
			Ability noRapier = abilities.getContextVariation(NO_RAPIER_ABILITY);
			if (noRapier != null) {
				return noRapier;
			}
		}
		return super.replaceWithSubAbility(context, abilities);
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		ConditionCheck check = super.checkSpecificConditions(context);
		if (!check.isPositive()) {
			return check;
		}

		boolean noRapier = lacksRapier(context);
		if (isNoRapierVariation()) {
			return noRapier ? ConditionCheck.POSITIVE : ConditionCheck.NEGATIVE;
		}
		if (noRapier) {
			return ConditionCheck.createNegative("chariot_rapier");
		}
		return ConditionCheck.POSITIVE;
	}

	private boolean isNoRapierVariation() {
		return NO_RAPIER_ABILITY.equals(name());
	}

	private static boolean lacksRapier(Power<?> context) {
		StandPower standPower = PowerClass.STAND.cast(context);
		if (standPower == null) {
			return false;
		}
		LivingEntity user = standPower.getUser();
		if (user == null) {
			return false;
		}
		SilverChariotState state = SilverChariotState.get(user);
		return state != null && !state.hasRapier();
	}

	public static class SilverChariotLightAttack extends StandEntityPunchAbility.StandEntityPunch {

		public SilverChariotLightAttack(EntityActionType ability) {
			super(ability);
		}

		@Override
		protected Holder<SoundEvent> getPunchImpactSound(ActionTarget target) {
			return usesRapierSweepSound() ? null : super.getPunchImpactSound(target);
		}

		@Override
		protected SoundEvent getPunchSwingSound(StandEntity stand) {
			return usesRapierSweepSound() ? ModSoundEvents.SILVER_CHARIOT_SWEEP_LIGHT.get() : super.getPunchSwingSound(stand);
		}

		@Override
		protected float getPunchSwingPitch(StandEntity stand) {
			return usesRapierSweepSound() ? 0.9F + stand.getRandom().nextFloat() * 0.2F : super.getPunchSwingPitch(stand);
		}

		private boolean usesRapierSweepSound() {
			AbilityId abilityId = ability.getAbilityId();
			return abilityId == null || !NO_RAPIER_ABILITY.equals(abilityId.nameInMoveset());
		}
	}
}
