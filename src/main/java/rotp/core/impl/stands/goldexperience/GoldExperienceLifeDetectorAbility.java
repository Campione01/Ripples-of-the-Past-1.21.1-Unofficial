package rotp.core.impl.stands.goldexperience;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.standpower.StandInstance.StandPart;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.NoPoseStandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandEntityAbility.AutoSummonMode;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

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

	// 1.16 GoldExperienceLifeDetector had only a stamina cost per tick (staminaCostTick), which a Stand-bearing mob
	// did not compare with its stamina (getStaminaCost 0)
	@Override
	public float getStaminaCost(Power<?> context) {
		return 0.0F;
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
			// 1.16 StandEntityTask paid each perform tick and ignored the result: an empty bar stays at 0
			StandAbilityStamina.consume(ability, StandPower.get(user), STAMINA_COST_TICK, true);
		}

		// 1.16 GoldExperienceLifeDetector was a holdType Stand action
		@Override
		public boolean isHoldTypeStandAction() {
			return true;
		}

		@Override
		public void onButtonStopHold() {
			if (getPhase() == ActionPhase.PERFORM) {
				startRecovery();
			}
		}
	}
}
