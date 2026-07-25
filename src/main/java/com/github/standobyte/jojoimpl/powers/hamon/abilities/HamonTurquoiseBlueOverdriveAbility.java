package com.github.standobyte.jojoimpl.powers.hamon.abilities;

import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData;
import com.github.standobyte.jojoimpl.powers.hamon.ModHamonSkills;
import com.github.standobyte.jojoimpl.powers.hamon.entity.HamonTurquoiseBlueOverdriveEntity;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class HamonTurquoiseBlueOverdriveAbility extends HamonActionRuntimeAbility {
	private static final float ENERGY_COST = 1000.0F;

	public HamonTurquoiseBlueOverdriveAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, TurquoiseBlueOverdriveInstance::new);
		setDefaultPhaseLength(ActionPhase.WINDUP, 10);
		setDefaultPhaseLength(ActionPhase.PERFORM, 6);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 4);
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
		return user.isInWaterOrBubble() ? ConditionCheck.POSITIVE : ConditionCheck.createNegative("underwater");
	}

	public static class TurquoiseBlueOverdriveInstance extends HamonActionRuntimeAbility.HamonRuntimeActionInstance {
		private boolean capturedPreRuntimeState;
		private float preRuntimeEnergy;
		private float preRuntimeEfficiency;
		private float preRuntimeControlRatio;

		public TurquoiseBlueOverdriveInstance(EntityActionType ability) { super(ability); }

		@Override
		protected void _onTick() {
			capturePreRuntimeState();
			super._onTick();
		}

		private void capturePreRuntimeState() {
			if (capturedPreRuntimeState || getPhase() != ActionPhase.PERFORM || getPhaseTick() >= 1
					|| level().isClientSide() || hamonAbility() == null) {
				return;
			}
			LivingEntity user = getPowerUser();
			if (user == null) {
				return;
			}
			Power<?> context = hamonAbility().getUserPower(user);
			HamonData hamon = hamonAbility().getHamonData(context);
			if (hamon != null) {
				preRuntimeEnergy = hamon.getEnergy();
				preRuntimeEfficiency = hamon.getActionEfficiency(ENERGY_COST, true,
						ModHamonSkills.TURQUOISE_BLUE_OVERDRIVE.get(), user);
				preRuntimeControlRatio = (float) hamon.getHamonControlLevel() / (float) HamonData.MAX_STAT_LEVEL;
				capturedPreRuntimeState = true;
			}
		}

		@Override
		public void actionPerformStart() {
			Level level = level();
			if (level.isClientSide()) return;
			LivingEntity user = getPowerUser();
			if (user == null || hamonAbility() == null || !user.isInWaterOrBubble()) {
				return;
			}
			if (!capturedPreRuntimeState) {
				capturePreRuntimeState();
			}
			Power<?> context = hamonAbility().getUserPower(user);
			HamonData hamon = hamonAbility().getHamonData(context);
			if (hamon == null) {
				return;
			}
			float efficiency = preRuntimeEfficiency > 0.0F ? preRuntimeEfficiency
					: hamon.getActionEfficiency(ENERGY_COST, true, ModHamonSkills.TURQUOISE_BLUE_OVERDRIVE.get(), user);
			float controlRatio = capturedPreRuntimeState ? preRuntimeControlRatio
					: (float) hamon.getHamonControlLevel() / (float) HamonData.MAX_STAT_LEVEL;
			float pointsEnergy = capturedPreRuntimeState ? preRuntimeEnergy : hamon.getEnergy();
			user.swing(InteractionHand.MAIN_HAND, true);
			HamonTurquoiseBlueOverdriveEntity overdriveWave = new HamonTurquoiseBlueOverdriveEntity(level, user)
					.setRadius(1.0F + 2.5F * controlRatio * efficiency)
					.setDamage(1.0F * efficiency)
					.setPoints(Math.min(ENERGY_COST, pointsEnergy) * efficiency)
					.setDuration(30 + (int) (70.0F * controlRatio));
			overdriveWave.shootFromRotation(user, 1.5F, 0.0F);
			level.addFreshEntity(overdriveWave);
		}
	}
}

