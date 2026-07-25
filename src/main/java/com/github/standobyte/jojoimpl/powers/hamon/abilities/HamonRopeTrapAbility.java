package com.github.standobyte.jojoimpl.powers.hamon.abilities;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.powersystem.ability.Ability;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData;
import com.github.standobyte.jojoimpl.powers.hamon.ModHamonSkills;
import com.github.standobyte.jojoimpl.powers.hamon.entity.HamonBlockChargeEntity;
import com.google.common.collect.ImmutableMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class HamonRopeTrapAbility extends Ability {
	private static final float STRING_CHARGE_COST = 10.0F;
	private static final Map<BooleanProperty, Direction> PROPERTY_TO_DIRECTION = ImmutableMap.of(
			TripWireBlock.EAST, Direction.EAST,
			TripWireBlock.SOUTH, Direction.SOUTH,
			TripWireBlock.WEST, Direction.WEST,
			TripWireBlock.NORTH, Direction.NORTH);

	public HamonRopeTrapAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId);
	}

	@Override
	public boolean addToControlSchemeEditing() {
		return false;
	}

	public static boolean ropeTrap(LivingEntity user, BlockPos pos, BlockState blockState, Level level, HamonData hamon) {
		if (level.isClientSide() || !hamon.isSkillLearned(ModHamonSkills.ROPE_TRAP.get())) {
			return false;
		}
		float efficiency = hamon.getActionEfficiency(STRING_CHARGE_COST, false, ModHamonSkills.ROPE_TRAP.get(), user);
		int chargeTicks = 40 + (int) (160.0F * hamon.getHamonStrengthLevel() / (float) HamonData.MAX_STAT_LEVEL * efficiency);
		float chargeDamage = 0.02F * hamon.getHamonDamageMultiplier() * efficiency;
		createChargedCobweb(user, pos, blockState, level, 64, null, hamon, chargeTicks, chargeDamage);
		hamon.syncOnUpdate(user);
		return true;
	}

	private static void createChargedCobweb(LivingEntity user, BlockPos pos, BlockState blockState, Level level,
			int range, @Nullable Direction from, HamonData hamon, int chargeTicks, float chargeDamage) {
		if (range <= 0 || !blockState.is(Blocks.TRIPWIRE) || !hamon.consumeEnergy(STRING_CHARGE_COST, user)) {
			return;
		}
		hamon.hamonPointsFromAction(HamonData.HamonStat.CONTROL, STRING_CHARGE_COST / 2.0F);
		List<Direction> directions = new ArrayList<>(4);
		for (Map.Entry<BooleanProperty, Direction> entry : PROPERTY_TO_DIRECTION.entrySet()) {
			BooleanProperty property = entry.getKey();
			Direction direction = entry.getValue();
			if (direction != from && blockState.hasProperty(property) && blockState.getValue(property)) {
				directions.add(direction);
			}
		}
		level.setBlock(pos, Blocks.COBWEB.defaultBlockState(), 3);
		HamonBlockChargeEntity chargeEntity = new HamonBlockChargeEntity(level, pos);
		chargeEntity.setCharge(chargeDamage, chargeTicks, user, STRING_CHARGE_COST / 2.0F);
		level.addFreshEntity(chargeEntity);
		for (Direction direction : directions) {
			BlockPos nextPos = pos.relative(direction);
			createChargedCobweb(user, nextPos, level.getBlockState(nextPos), level,
					range - 1, direction.getOpposite(), hamon, chargeTicks, chargeDamage);
		}
	}
}
