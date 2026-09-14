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

import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class HamonPlantInfusionAbility extends HamonActionRuntimeAbility {
	private static final float ENERGY_COST = 200.0F;

	public HamonPlantInfusionAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, PlantInfusionInstance::new);
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
		ActionTarget target = HamonAbilityHelpers.getAimTarget(user, user.level());
		if (target.getType() != TargetType.BLOCK) {
			return ConditionCheck.createNegative("block_target");
		}
		ConditionCheck block = HamonOrganismInfusionAbility.canChargeBlock(user.level(), target.getBlockPos());
		if (!block.isPositive()) {
			return block;
		}
		HamonData hamon = getHamonData(context);
		return hamon != null && (isCreative(context) || hamon.hasEnergy(ENERGY_COST))
				? ConditionCheck.POSITIVE : ConditionCheck.createNegative("no_energy_hamon");
	}

	public static class PlantInfusionInstance extends EntityActionInstance {
		public PlantInfusionInstance(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void onActionSet(EntityActionInstance prevAction) {
			LivingEntity user = getPowerUser();
			if (user != null) {
				captureActionTargetFromAim(user);
			}
		}

		@Override
		public void actionPerformStart() {
			Level level = level();
			if (level.isClientSide()) {
				return;
			}
			LivingEntity user = getPowerUser();
			if (user == null || !(ability instanceof HamonPlantInfusionAbility hamonAbility)) {
				return;
			}
			ActionTarget target = getActionTargetSnapshot(level);
			if (target.getType() != TargetType.BLOCK) {
				return;
			}
			Power<?> context = hamonAbility.getUserPower(user);
			HamonData hamon = hamonAbility.getHamonData(context);
			if (hamon == null) {
				return;
			}
			float efficiency = HamonOrganismInfusionAbility.consumeForEfficiency(
					context, hamon, user, ENERGY_COST, ModHamonSkills.PLANT_BLOCK_INFUSION.get());
			if (efficiency <= 0.0F) {
				return;
			}
			HamonOrganismInfusionAbility.chargeBlock(level, target.getBlockPos(), user, hamon, efficiency, ENERGY_COST);
			if (hamon.isSkillLearned(ModHamonSkills.HAMON_SPREAD.get())) {
				for (Direction direction : Direction.values()) {
					HamonOrganismInfusionAbility.chargeBlock(level, target.getBlockPos().relative(direction), user, hamon, efficiency, ENERGY_COST);
				}
			}
		}
	}
}
