package rotp.core.impl.stands.silverchariot;

import rotp.core.powersystem.standpower.StandInstance.StandPart;

import rotp.core.customobjects.AfterimageEntity;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.ArmoredStandStats;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.NoPoseStandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandEntity;

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
