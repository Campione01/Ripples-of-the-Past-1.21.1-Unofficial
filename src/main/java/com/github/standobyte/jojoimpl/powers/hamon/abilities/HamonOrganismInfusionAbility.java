package com.github.standobyte.jojoimpl.powers.hamon.abilities;

import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.subsystems.target.ActionTarget;
import com.github.standobyte.jojo.subsystems.target.ActionTarget.TargetType;
import com.github.standobyte.jojoimpl.powers.hamon.EntityHamonChargeState;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData;
import com.github.standobyte.jojoimpl.powers.hamon.HamonUtil;
import com.github.standobyte.jojoimpl.powers.hamon.ModHamonSkills;
import com.github.standobyte.jojoimpl.powers.hamon.entity.HamonBlockChargeEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class HamonOrganismInfusionAbility extends HamonActionRuntimeAbility {
	private static final float ENTITY_ENERGY_COST = 1000.0F;
	private static final float PLANT_ENERGY_COST = 200.0F;

	public HamonOrganismInfusionAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, OrganismInfusionInstance::new);
		setDefaultPhaseLength(ActionPhase.WINDUP, 6);
		setDefaultPhaseLength(ActionPhase.PERFORM, 4);
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
		ActionTarget target = HamonAbilityHelpers.getAimTarget(user, user.level());
		return checkTarget(target, user, context);
	}

	protected ConditionCheck checkTarget(ActionTarget target, LivingEntity user, Power<?> context) {
		if (target.getType() == TargetType.ENTITY && target.getMainEntity() instanceof LivingEntity living) {
			return HamonUtil.isLiving(living) ? hasEnergy(context, ENTITY_ENERGY_COST) : ConditionCheck.createNegative("living_mob");
		}
		if (target.getType() == TargetType.BLOCK) {
			ConditionCheck plant = canChargeBlock(user.level(), target.getBlockPos());
			return plant.isPositive() ? hasEnergy(context, PLANT_ENERGY_COST) : plant;
		}
		return ConditionCheck.NEGATIVE;
	}

	protected ConditionCheck hasEnergy(Power<?> context, float energyCost) {
		HamonData hamon = getHamonData(context);
		return hamon != null && (isCreative(context) || hamon.hasEnergy(energyCost))
				? ConditionCheck.POSITIVE : ConditionCheck.createNegative("no_energy_hamon");
	}

	public static ConditionCheck canChargeBlock(Level level, BlockPos blockPos) {
		BlockState blockState = level.getBlockState(blockPos);
		Block block = blockState.getBlock();
		if (block instanceof TurtleEggBlock) {
			return ConditionCheck.createNegative("living_plant");
		}
		boolean livingBlock = HamonUtil.isBlockLiving(blockState);
		if (!livingBlock && block instanceof FlowerPotBlock && !blockState.is(Blocks.FLOWER_POT)) {
			livingBlock = true;
		}
		return livingBlock ? ConditionCheck.POSITIVE : ConditionCheck.createNegative("living_plant");
	}

	public static int chargeTicks(HamonData hamon, float efficiency) {
		return 100 + Mth.floor(1100.0F * hamon.getHamonStrengthLevel() / (float) HamonData.MAX_STAT_LEVEL
				* efficiency * efficiency);
	}

	public static float consumeForEfficiency(Power<?> context, HamonData hamon, LivingEntity user, float energyCost, com.github.standobyte.jojoimpl.powers.hamon.HamonSkill skill) {
		if (user instanceof net.minecraft.world.entity.player.Player player && player.getAbilities().instabuild) {
			return 1.0F;
		}
		float efficiency = hamon.getActionEfficiency(energyCost, true, skill, user);
		if (efficiency <= 0.0F || hamon.getHamonEnergyUsageEfficiency(energyCost, true, user) <= 0.0F) {
			return 0.0F;
		}
		hamon.syncOnUpdate(user);
		return efficiency;
	}

	public static boolean chargeEntity(LivingEntity user, LivingEntity target, HamonData hamon, float efficiency, float energySpent) {
		EntityHamonChargeState state = EntityHamonChargeState.get(target);
		if (state.hasHamonCharge()) {
			return false;
		}
		state.setHamonCharge(hamon.getHamonDamageMultiplier() * efficiency, chargeTicks(hamon, efficiency), user, energySpent);
		return true;
	}

	public static void chargeBlock(Level level, BlockPos blockPos, LivingEntity user, HamonData hamon, float efficiency, float energySpent) {
		if (!canChargeBlock(level, blockPos).isPositive()) {
			return;
		}
		level.getEntitiesOfClass(HamonBlockChargeEntity.class, new AABB(blockPos), Entity::isAlive)
				.forEach(Entity::discard);
		HamonBlockChargeEntity charge = new HamonBlockChargeEntity(level, blockPos);
		charge.setCharge(hamon.getHamonDamageMultiplier() * efficiency, chargeTicks(hamon, efficiency), user, energySpent);
		level.addFreshEntity(charge);
	}

	public static class OrganismInfusionInstance extends EntityActionInstance {
		public OrganismInfusionInstance(EntityActionType ability) {
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
			if (user == null || !(ability instanceof HamonOrganismInfusionAbility hamonAbility)) {
				return;
			}
			ActionTarget target = getActionTargetSnapshot(level);
			Power<?> context = hamonAbility.getUserPower(user);
			HamonData hamon = hamonAbility.getHamonData(context);
			if (hamon == null) {
				return;
			}
			if (target.getType() == TargetType.ENTITY && target.getMainEntity() instanceof LivingEntity livingTarget) {
				if (!HamonUtil.isLiving(livingTarget)) {
					return;
				}
				float efficiency = consumeForEfficiency(context, hamon, user, ENTITY_ENERGY_COST, ModHamonSkills.ANIMAL_INFUSION.get());
				if (efficiency > 0.0F) {
					if (chargeEntity(user, livingTarget, hamon, efficiency, ENTITY_ENERGY_COST)) {
						user.swing(InteractionHand.MAIN_HAND, true);
					}
				}
			}
			else if (target.getType() == TargetType.BLOCK) {
				float efficiency = consumeForEfficiency(context, hamon, user, PLANT_ENERGY_COST, ModHamonSkills.PLANT_BLOCK_INFUSION.get());
				if (efficiency > 0.0F) {
					user.swing(InteractionHand.MAIN_HAND, true);
					chargeBlock(level, target.getBlockPos(), user, hamon, efficiency, PLANT_ENERGY_COST);
					if (hamon.isSkillLearned(ModHamonSkills.HAMON_SPREAD.get())) {
						for (Direction direction : Direction.values()) {
							chargeBlock(level, target.getBlockPos().relative(direction), user, hamon, efficiency, PLANT_ENERGY_COST);
						}
					}
				}
			}
		}
	}
}
