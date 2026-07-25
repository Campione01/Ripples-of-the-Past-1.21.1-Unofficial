package com.github.standobyte.jojoimpl.stands.silverchariot;

import com.github.standobyte.jojo.powersystem.standpower.StandInstance.StandPart;

import com.github.standobyte.jojo.customobjects.AfterimageEntity;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.powersystem.standpower.ArmoredStandStats;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.StandStats;
import com.github.standobyte.jojo.powersystem.standpower.StandUtil;
import com.github.standobyte.jojo.powersystem.standpower.entity.NoPoseStandEntityAbility;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class SilverChariotTakeOffArmorAbility extends NoPoseStandEntityAbility {

	private static final double DEFAULT_ARMOR_POWER = 20.0;

	public SilverChariotTakeOffArmorAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, TakeOffArmorStrike::new);
		partsRequired(StandPart.MAIN_BODY);
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		ConditionCheck check = super.checkSpecificConditions(context);
		if (!check.isPositive()) {
			return check;
		}
		StandPower standPower = PowerClass.STAND.cast(context);
		if (standPower == null) {
			return ConditionCheck.NEGATIVE;
		}
		StandEntity stand = standPower.getSummonedStandEntity();
		if (stand != null && stand.isArmsOnlyMode()) {
			return ConditionCheck.NEGATIVE;
		}
		LivingEntity user = standPower.getUser();
		if (user == null) {
			return ConditionCheck.NEGATIVE;
		}
		SilverChariotState state = SilverChariotState.get(user);
		if (state == null) {
			return ConditionCheck.NEGATIVE;
		}
		if (!state.hasArmor()) {
			return ConditionCheck.createNegative("chariot_armor");
		}
		return ConditionCheck.POSITIVE;
	}

	@Override
	protected ConditionCheck checkStandEntityConditions(StandPower standPower, StandEntity standEntity) {
		ConditionCheck check = super.checkStandEntityConditions(standPower, standEntity);
		if (!check.isPositive()) {
			return check;
		}
		if (standEntity.isArmsOnlyMode()) {
			return ConditionCheck.NEGATIVE;
		}
		LivingEntity user = standPower.getUser();
		if (user == null) {
			return ConditionCheck.NEGATIVE;
		}
		SilverChariotState state = SilverChariotState.get(user);
		if (state == null) {
			return ConditionCheck.NEGATIVE;
		}
		return state.hasArmor() ? ConditionCheck.POSITIVE : ConditionCheck.createNegative("chariot_armor");
	}

	public static class TakeOffArmorStrike extends EntityActionInstance {

		public TakeOffArmorStrike(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void actionPerformStart() {
			Level level = level();
			if (level.isClientSide()) {
				return;
			}
			LivingEntity user = getPowerUser();
			if (user == null) {
				return;
			}
			if (!(performer instanceof StandEntity stand) || stand.isArmsOnlyMode()) {
				return;
			}
			SilverChariotState state = SilverChariotState.get(user);
			if (state == null || !state.hasArmor()) {
				return;
			}

			ArmoredStandStats armored = state.armoredStats();
			if (armored == null) {
				armored = new ArmoredStandStats(new StandStats.Builder()
						.power(9).speed(17.5).range(10, 10).durability(12).precision(16).build(),
						DEFAULT_ARMOR_POWER, true);
			}
			ArmoredStandStats stripped = armored.withoutArmor();
			state.setArmoredStats(stripped);
			state.setHasArmor(false);
			stand.refreshSilverChariotStateAfterMutation(user);
			AfterimageEntity.addAfterimages(stand, 10, -1);

			StandUtil.playStandEntitySound(stand, ModSoundEvents.SILVER_CHARIOT_ARMOR_OFF, 1.0F, 1.0F);
		}
	}
}
