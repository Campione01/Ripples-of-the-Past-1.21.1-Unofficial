package com.github.standobyte.jojoimpl.stands._entitybase;

import com.github.standobyte.jojo.client.input.AbilityInputState;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.AbilityUsageGroup;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.entityaction.ActionAnimIdentifier;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.StandInstance.StandPart;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntityAbility;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntityAbility.AutoSummonMode;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandOffsetFromUser;

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
		if (stand != null && !(stand.canStartBlocking() || stand.isStandBlocking())) {
			return ConditionCheck.NEGATIVE;
		}
		return super.checkSpecificConditions(context);
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
