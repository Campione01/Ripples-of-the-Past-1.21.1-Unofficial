package com.github.standobyte.jojoimpl.powers.hamon.abilities;

import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojoimpl.powers.hamon.ModHamonSkills;
import com.github.standobyte.jojoimpl.powers.hamon.entity.HamonZoomPunchEntity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class HamonZoomPunchAbility extends HamonActionRuntimeAbility {
	private static final float ENERGY_COST = 450.0F;
	private static final float HIT_COST = 150.0F;
	private static final float HAMON_DAMAGE = 0.7F;
	private static final int COOLDOWN_TICKS = 14;

	public HamonZoomPunchAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, ZoomPunchInstance::new);
		setDefaultPhaseLength(ActionPhase.WINDUP, 0);
		setDefaultPhaseLength(ActionPhase.PERFORM, 1);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
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
		return user.getMainHandItem().isEmpty() ? ConditionCheck.POSITIVE : ConditionCheck.createNegative("hand");
	}

	public static class ZoomPunchInstance extends HamonActionRuntimeAbility.HamonRuntimeActionInstance {
		private boolean capturedPreRuntimeState;
		private float preRuntimeEnergy;
		private float preRuntimeEfficiency;
		private float baseUsageStatPoints;

		public ZoomPunchInstance(EntityActionType ability) { super(ability); }

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
			if (user == null) {
				return;
			}
			PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).ifPresent(hamon -> {
				preRuntimeEnergy = hamon.getEnergy();
				preRuntimeEfficiency = hamon.getActionEfficiency(ENERGY_COST, true, ModHamonSkills.ZOOM_PUNCH.get(), user);
				baseUsageStatPoints = Math.min(ENERGY_COST, preRuntimeEnergy) * preRuntimeEfficiency;
				capturedPreRuntimeState = true;
			});
		}

		@Override
		public void actionPerformStart() {
			Level level = level();
			if (level.isClientSide()) return;
			LivingEntity user = getPowerUser();
			if (user == null) return;
			if (!capturedPreRuntimeState) {
				capturePreRuntimeState();
			}
			PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).ifPresent(hamon -> {
				float efficiency = preRuntimeEfficiency > 0.0F ? preRuntimeEfficiency
						: hamon.getActionEfficiency(ENERGY_COST, true, ModHamonSkills.ZOOM_PUNCH.get(), user);
				float zoomPunchMaxLength = 4.0F + (4.0F + hamon.getHamonControlLevel() * 0.1F) * efficiency;
				int duration = Math.max(COOLDOWN_TICKS, 1);
				float projSpeed = 2.0F * zoomPunchMaxLength / duration * (0.4F + 0.6F * efficiency);
				HamonZoomPunchEntity zoomPunch = new HamonZoomPunchEntity(user, level)
						.setSpeed(projSpeed)
						.setDuration(duration)
						.setHamonDamageOnHit(HAMON_DAMAGE, HIT_COST, preRuntimeEnergy <= 0.0F)
						.setBaseUsageStatPoints(baseUsageStatPoints);
				level.addFreshEntity(zoomPunch);
			});
		}
	}
}

