package rotp.core.impl.stands._entitybase;

import rotp.core.client.input.AbilityInputState;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandEntityAbility.AutoSummonMode;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.subsystems.entity_grab.LivingComponentGrab;

import net.minecraft.world.phys.Vec3;

public class StandEntityBlockAbility extends StandEntityAbility {
	private static final ActionAnimIdentifier BLOCK_ANIM = ActionAnimIdentifier.getOrCreate("block", false);

	public StandEntityBlockAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, StandEntityBlock::new);
		usageGroup = AbilityUsageGroup.COMBAT;
		setButtonHoldPhase(ActionPhase.PERFORM);
		standAutoSummonMode(AutoSummonMode.ARMS);
		partsRequired(StandPart.ARMS);
	}

	@Override
	public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
		return BLOCK_ANIM;
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		StandPower standPower = PowerClass.STAND.cast(context);
		StandEntity stand = standPower != null ? standPower.getSummonedStandEntity() : null;
		if (stand != null && LivingComponentGrab.getEntityGrabbedBy(stand) != null) {
			return ConditionCheck.NEGATIVE;
		}
		if (stand != null && !(stand.canStartBlocking() || stand.isStandBlocking())) {
			return ConditionCheck.NEGATIVE;
		}
		return super.checkSpecificConditions(context);
	}

	@Override
	protected ConditionCheck checkStandEntityConditions(
			StandPower standPower, StandEntity standEntity) {
		if (LivingComponentGrab.getEntityGrabbedBy(standEntity) != null) {
			return ConditionCheck.NEGATIVE;
		}
		return super.checkStandEntityConditions(standPower, standEntity);
	}

	@Override
	public AbilityInputState cl_abilityInputState(Power<?> context) {
		AbilityInputState state = super.cl_abilityInputState(context);
		state.setFlag(AbilityInputState.HIGH_PRIORITY, true);
		return state;
	}

	public static class StandEntityBlock extends EntityActionInstance {

		public StandEntityBlock(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void onActionSet(EntityActionInstance prevAction) {
			if (performer instanceof StandEntity standEntity) {
				setStandOffset(new Vec3(0, standEntity.Y_OFFSET, 0.3), StandOffsetFromUser.Rotations.HEAD, false);
			}
		}

		@Override
		public void onSetPhase(ActionPhase newPhase) {
			userWalkSpeed = newPhase == ActionPhase.PERFORM ? 0.3F : 1;
		}

		@Override
		public void onButtonStopHold() {
			startRecovery();
		}

		@Override
		public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
			return cancellingAbility != ability;
		}
	}
}
