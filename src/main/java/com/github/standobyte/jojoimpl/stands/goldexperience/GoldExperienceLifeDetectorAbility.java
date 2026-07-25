package com.github.standobyte.jojoimpl.stands.goldexperience;

import com.github.standobyte.jojo.powersystem.standpower.StandInstance.StandPart;

import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.entity.NoPoseStandEntityAbility;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntityAbility.AutoSummonMode;
import com.github.standobyte.jojoimpl.stands._entitybase.StandAbilityStamina;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class GoldExperienceLifeDetectorAbility extends NoPoseStandEntityAbility {

	private static final float STAMINA_COST_TICK = 0.5F;

	public GoldExperienceLifeDetectorAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, LifeDetectorScan::new);
		partsRequired(StandPart.MAIN_BODY);
		setButtonHoldPhase(ActionPhase.PERFORM);
		standAutoSummonMode(AutoSummonMode.OFF_ARM);
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		ConditionCheck check = super.checkSpecificConditions(context);
		return check.isPositive() ? StandAbilityStamina.check(context, STAMINA_COST_TICK) : check;
	}

	public static class LifeDetectorScan extends EntityActionInstance {

		public LifeDetectorScan(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void actionTick() {
			if (getPhase() != ActionPhase.PERFORM) {
				return;
			}
			Level level = level();
			if (level.isClientSide()) {
				return;
			}
			LivingEntity user = getPowerUser();
			if (user == null) {
				return;
			}
			StandPower standPower = StandPower.get(user);
			if (!StandAbilityStamina.consume(ability, standPower, STAMINA_COST_TICK, true)) {
				startRecovery();
			}
		}

		@Override
		public void onButtonStopHold() {
			if (getPhase() == ActionPhase.PERFORM) {
				startRecovery();
			}
		}
	}
}
