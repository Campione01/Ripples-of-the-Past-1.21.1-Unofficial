package rotp.core.impl.powers.hamon.abilities;

import javax.annotation.Nullable;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.impl.powers.hamon.entity.HamonBubbleBarrierEntity;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class HamonBubbleBarrierAbility extends HamonActionRuntimeAbility {

	public HamonBubbleBarrierAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, BubbleBarrierInstance::new);
		setDefaultPhaseLength(ActionPhase.WINDUP, 8);
		setDefaultPhaseLength(ActionPhase.PERFORM, 6);
		setButtonHoldPhase(ActionPhase.PERFORM);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 4);
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		ConditionCheck check = super.checkSpecificConditions(context);
		if (!check.isPositive()) {
			return check;
		}
		return context.getUser() instanceof LivingEntity user ? HamonSoapHelper.checkSoap(user) : ConditionCheck.NEGATIVE;
	}

	// 1.16 HamonBubbleBarrier.checkHeldItems: soap, checked on every held tick too.
	@Override
	protected ConditionCheck checkHeldItems(LivingEntity user) {
		return HamonSoapHelper.checkSoap(user);
	}

	public static class BubbleBarrierInstance extends HamonActionRuntimeAbility.HamonHeldActionInstance {
		@Nullable private HamonBubbleBarrierEntity chargingBarrier;

		public BubbleBarrierInstance(EntityActionType ability) { super(ability); }

		// 1.16 HamonBubbleBarrier.startedHolding: the barrier appears when the hold starts and grows in place
		// while it charges; the barrier removes itself if the hold ends before it fires.
		@Override
		public void onActionSet(EntityActionInstance prevAction) {
			Level level = level();
			LivingEntity user = getPowerUser();
			if (user != null && !level.isClientSide()) {
				chargingBarrier = new HamonBubbleBarrierEntity(level, user).setCharging();
				level.addFreshEntity(chargingBarrier);
			}
		}

		public boolean isChargingBarrier(HamonBubbleBarrierEntity barrier) {
			return barrier == chargingBarrier && !isOver()
					&& (getPhase() == ActionPhase.WINDUP || getPhase() == ActionPhase.PERFORM);
		}

		@Override
		public void actionPerformStart() {
			Level level = level();
			LivingEntity user = getPowerUser();
			if (user == null) return;
			if (level.isClientSide()) {
				user.swing(InteractionHand.MAIN_HAND, true);
				return;
			}
			if (HamonSoapHelper.consumeSoap(user, 50) == HamonSoapHelper.TookSoapFrom.NONE) {
				forceStop();
				syncPhaseChanges();
				return;
			}
			// The barrier that grew during the charge goes where the user looks when it fires.
			if (chargingBarrier != null && chargingBarrier.isAlive()) {
				chargingBarrier.shootFromCharge(user);
			}
			chargingBarrier = null;
		}
	}
}
