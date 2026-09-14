package rotp.core.impl.powers.hamon.abilities;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
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

	public static class BubbleBarrierInstance extends HamonActionRuntimeAbility.HamonHeldActionInstance {
		public BubbleBarrierInstance(EntityActionType ability) { super(ability); }

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
			HamonBubbleBarrierEntity bubbleBarrier = new HamonBubbleBarrierEntity(level, user);
			bubbleBarrier.shootFromRotation(user, 1.0F, 0.0F);
			level.addFreshEntity(bubbleBarrier);
		}
	}
}
