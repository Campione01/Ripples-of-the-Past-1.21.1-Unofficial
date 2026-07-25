package com.github.standobyte.jojoimpl.powers.hamon.abilities;

import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.subsystems.target.ActionTarget;
import com.github.standobyte.jojo.subsystems.target.ActionTarget.TargetType;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData;
import com.github.standobyte.jojoimpl.powers.hamon.ModHamonSkills;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class HamonOverdriveAbility extends HamonActionRuntimeAbility {
	private static final float ENERGY_COST = 600.0F;
	private static final float BASE_DAMAGE = 2.0F;

	public HamonOverdriveAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, OverdriveInstance::new);
		setDefaultPhaseLength(ActionPhase.WINDUP, 5);
		setDefaultPhaseLength(ActionPhase.PERFORM, 4);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 3);
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		ConditionCheck check = super.checkSpecificConditions(context);
		if (!check.isPositive()) {
			return check;
		}
		LivingEntity user = context != null ? context.getUser() : null;
		if (user == null) {
			return ConditionCheck.NEGATIVE;
		}
		if (!user.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()) {
			return ConditionCheck.createNegative("hand");
		}
		ActionTarget target = getAimTarget(user, user.level());
		return target.getType() == TargetType.ENTITY && target.getMainEntity() instanceof LivingEntity
				? ConditionCheck.POSITIVE : ConditionCheck.NEGATIVE;
	}

	static ActionTarget getAimTarget(LivingEntity user, Level level) {
		var aim = LivingComponentAction.getAim(user);
		ActionTarget target = aim != null ? aim.getTarget() : ActionTarget.EMPTY;
		return target != null ? target.resolveEntityId(level) : ActionTarget.EMPTY;
	}

	public static class OverdriveInstance extends HamonActionRuntimeAbility.HamonRuntimeActionInstance {
		private boolean capturedPreRuntimeState;
		private float preRuntimeEnergy;
		private float preRuntimeEfficiency;

		public OverdriveInstance(EntityActionType ability) { super(ability); }

		@Override
		public void onActionSet(EntityActionInstance prevAction) {
			LivingEntity user = getPowerUser();
			if (user != null) {
				captureActionTargetFromAim(user);
			}
		}

		@Override
		protected void _onTick() {
			capturePreRuntimeState();
			super._onTick();
		}

		private void capturePreRuntimeState() {
			if (capturedPreRuntimeState || getPhase() != ActionPhase.PERFORM || getPhaseTick() >= 1
					|| level().isClientSide()) {
				return;
			}
			LivingEntity user = getPowerUser();
			if (user == null || hamonAbility() == null) {
				return;
			}
			Power<?> context = hamonAbility().getUserPower(user);
			HamonData hamon = hamonAbility().getHamonData(context);
			if (hamon != null) {
				preRuntimeEnergy = hamon.getEnergy();
				preRuntimeEfficiency = hamon.getActionEfficiency(ENERGY_COST, true,
						ModHamonSkills.OVERDRIVE.get(), user);
				capturedPreRuntimeState = true;
			}
		}

		@Override
		public void actionPerformStart() {
			Level level = level();
			if (level.isClientSide()) return;
			LivingEntity user = getPowerUser();
			if (user == null || hamonAbility() == null) return;
			ActionTarget target = getActionTargetSnapshot(level);
			if (target.getType() != TargetType.ENTITY || !(target.getMainEntity() instanceof LivingEntity livingTarget)) {
				return;
			}
			Power<?> context = hamonAbility().getUserPower(user);
			HamonData hamon = hamonAbility().getHamonData(context);
			if (hamon == null) {
				return;
			}
			float efficiency = preRuntimeEfficiency > 0.0F ? preRuntimeEfficiency
					: hamon.getActionEfficiency(ENERGY_COST, true, ModHamonSkills.OVERDRIVE.get(), user);
			user.swing(InteractionHand.MAIN_HAND, true);
			if (HamonAbilityHelpers.hamonHurt(livingTarget, user, BASE_DAMAGE * efficiency)) {
				float pointsEnergy = capturedPreRuntimeState ? preRuntimeEnergy : hamon.getEnergy();
				hamon.hamonPointsFromAction(HamonData.HamonStat.STRENGTH,
						Math.min(ENERGY_COST, pointsEnergy) * efficiency);
				hamon.syncOnUpdate(user);
			}
		}
	}
}
