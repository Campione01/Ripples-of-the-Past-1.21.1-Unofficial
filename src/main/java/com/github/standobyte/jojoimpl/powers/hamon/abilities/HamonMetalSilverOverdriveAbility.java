package com.github.standobyte.jojoimpl.powers.hamon.abilities;

import com.github.standobyte.jojo.init.ModParticles;
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

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class HamonMetalSilverOverdriveAbility extends HamonActionRuntimeAbility {
	private static final float ENERGY_COST = 1000.0F;
	private static final float BASE_DAMAGE = 2.0F;

	public HamonMetalSilverOverdriveAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, MetalSilverOverdriveInstance::new);
		setDefaultPhaseLength(ActionPhase.WINDUP, 8);
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
		return targetedByMSO(getAimTarget(user, user.level())) ? ConditionCheck.POSITIVE : ConditionCheck.NEGATIVE;
	}

	static boolean targetedByMSO(ActionTarget target) {
		return target.getType() == TargetType.ENTITY
				&& target.getMainEntity() instanceof LivingEntity livingTarget
				&& getDamageMultiplier(livingTarget) > 1.0F;
	}

	static float getDamageMultiplier(LivingEntity target) {
		float multiplier = 1.0F;
		for (EquipmentSlot slot : new EquipmentSlot[] {
				EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
			if (!target.getItemBySlot(slot).isEmpty()) {
				multiplier += 0.2F;
			}
		}
		for (InteractionHand hand : InteractionHand.values()) {
			if (HamonAbilityHelpers.isItemWeapon(target.getItemInHand(hand))) {
				multiplier += 0.2F;
				break;
			}
		}
		return multiplier;
	}

	static ActionTarget getAimTarget(LivingEntity user, Level level) {
		var aim = LivingComponentAction.getAim(user);
		ActionTarget target = aim != null ? aim.getTarget() : ActionTarget.EMPTY;
		return target != null ? target.resolveEntityId(level) : ActionTarget.EMPTY;
	}

	static boolean dealMetalSilverDamage(LivingEntity target, LivingEntity user, float damage) {
		boolean hurt = HamonAbilityHelpers.hamonHurt(target, user, damage);
		if (hurt) {
			sendSilverSparks(target, 8);
		}
		return hurt;
	}

	private static void sendSilverSparks(LivingEntity target, int count) {
		if (target.level() instanceof ServerLevel serverLevel) {
			serverLevel.sendParticles(ModParticles.HAMON_SPARK_SILVER.get(),
					target.getX(), target.getY(0.5D), target.getZ(), count,
					target.getBbWidth() * 0.25D, target.getBbHeight() * 0.25D, target.getBbWidth() * 0.25D, 0.05D);
		}
	}

	public static class MetalSilverOverdriveInstance extends HamonActionRuntimeAbility.HamonRuntimeActionInstance {
		private boolean capturedPreRuntimeState;
		private float preRuntimeEnergy;
		private float preRuntimeEfficiency;

		public MetalSilverOverdriveInstance(EntityActionType ability) { super(ability); }

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
						ModHamonSkills.METAL_SILVER_OVERDRIVE.get(), user);
				capturedPreRuntimeState = true;
			}
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
			ActionTarget target = getActionTargetSnapshot(level);
			if (target.getType() != TargetType.ENTITY || !(target.getMainEntity() instanceof LivingEntity livingTarget)) {
				return;
			}
			float multiplier = getDamageMultiplier(livingTarget);
			if (multiplier <= 1.0F || hamonAbility() == null) {
				return;
			}
			Power<?> context = hamonAbility().getUserPower(user);
			HamonData hamon = hamonAbility().getHamonData(context);
			float efficiency = preRuntimeEfficiency > 0.0F ? preRuntimeEfficiency
					: hamon != null ? hamon.getActionEfficiency(ENERGY_COST, true,
							ModHamonSkills.METAL_SILVER_OVERDRIVE.get(), user) : 1.0F;
			user.swing(InteractionHand.MAIN_HAND, true);
			if (dealMetalSilverDamage(livingTarget, user, BASE_DAMAGE * multiplier * efficiency)
					&& hamon != null) {
				hamon.hamonPointsFromAction(HamonData.HamonStat.STRENGTH, Math.min(ENERGY_COST, preRuntimeEnergy) * efficiency);
				hamon.syncOnUpdate(user);
			}
		}
	}
}

