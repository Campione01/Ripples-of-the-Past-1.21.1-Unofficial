package rotp.core.impl.powers.hamon.abilities;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class HamonMetalSilverOverdriveWeaponAbility extends HamonActionRuntimeAbility {
	private static final float ENERGY_COST = 750.0F;
	private static final float BASE_DAMAGE = 2.0F;

	public HamonMetalSilverOverdriveWeaponAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, MetalSilverWeaponInstance::new);
		setDefaultPhaseLength(ActionPhase.WINDUP, 5);
		setDefaultPhaseLength(ActionPhase.PERFORM, 5);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 3);
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		ConditionCheck check = super.checkSpecificConditions(context);
		if (!check.isPositive()) {
			return check;
		}
		LivingEntity user = context != null ? context.getUser() : null;
		ActionTarget target = user != null ? HamonMetalSilverOverdriveAbility.getAimTarget(user, user.level()) : ActionTarget.EMPTY;
		return user != null && HamonAbilityHelpers.isItemWeapon(user.getMainHandItem())
				&& target.getType() == TargetType.ENTITY && target.getMainEntity() instanceof LivingEntity
				? ConditionCheck.POSITIVE
				: ConditionCheck.NEGATIVE;
	}

	public static class MetalSilverWeaponInstance extends HamonActionRuntimeAbility.HamonRuntimeActionInstance {
		private boolean capturedPreRuntimeState;
		private float preRuntimeEnergy;
		private float preRuntimeEfficiency;

		public MetalSilverWeaponInstance(EntityActionType ability) { super(ability); }

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
			if (target.getType() != TargetType.ENTITY || !(target.getMainEntity() instanceof LivingEntity livingTarget)
					|| hamonAbility() == null) {
				return;
			}
			Power<?> context = hamonAbility().getUserPower(user);
			HamonData hamon = hamonAbility().getHamonData(context);
			float efficiency = preRuntimeEfficiency > 0.0F ? preRuntimeEfficiency
					: hamon != null ? hamon.getActionEfficiency(ENERGY_COST, true,
							ModHamonSkills.METAL_SILVER_OVERDRIVE.get(), user) : 1.0F;
			user.swing(InteractionHand.MAIN_HAND, true);
			if (HamonMetalSilverOverdriveAbility.dealMetalSilverDamage(livingTarget, user, BASE_DAMAGE * efficiency)
					&& hamon != null) {
				hamon.hamonPointsFromAction(HamonData.HamonStat.STRENGTH, Math.min(ENERGY_COST, preRuntimeEnergy) * efficiency);
				hamon.syncOnUpdate(user);
			}
		}
	}
}

