package rotp.core.impl.powers.hamon.abilities;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.entity.HamonBubbleEntity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class HamonBubbleLauncherAbility extends HamonActionRuntimeAbility {

	public HamonBubbleLauncherAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, BubbleLauncherInstance::new);
		setDefaultPhaseLength(ActionPhase.WINDUP, 5);
		setDefaultPhaseLength(ActionPhase.PERFORM, 4);
		setButtonHoldPhase(ActionPhase.PERFORM);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 3);
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		ConditionCheck check = super.checkSpecificConditions(context);
		if (!check.isPositive()) {
			return check;
		}
		return context.getUser() instanceof LivingEntity user ? HamonSoapHelper.checkSoap(user) : ConditionCheck.NEGATIVE;
	}

	// 1.16 HamonBubbleLauncher.checkHeldItems: soap, checked on every held tick too.
	@Override
	protected ConditionCheck checkHeldItems(LivingEntity user) {
		return HamonSoapHelper.checkSoap(user);
	}

	@Override
	protected void onHeldTick(HamonHeldActionInstance action, LivingEntity user, Power<?> context, HamonData hamon, int ticksHeld) {
		Level level = user.level();
		if (level.isClientSide()) {
			return;
		}
		HamonSoapHelper.TookSoapFrom soapSource = HamonSoapHelper.consumeSoap(user, 1);
		if (soapSource == HamonSoapHelper.TookSoapFrom.NONE) {
			action.forceStop();
			action.syncPhaseChanges();
			return;
		}
		int bubblesCount = soapSource == HamonSoapHelper.TookSoapFrom.BOTTLE ? 36 : 4;
		for (int i = 0; i < bubblesCount; i++) {
			HamonBubbleEntity bubbleEntity = new HamonBubbleEntity(user, level);
			float velocity = 0.1F + user.getRandom().nextFloat() * 0.5F;
			bubbleEntity.shootFromRotation(user, velocity, 16.0F);
			level.addFreshEntity(bubbleEntity);
		}
	}

	public static class BubbleLauncherInstance extends HamonActionRuntimeAbility.HamonHeldActionInstance {
		public BubbleLauncherInstance(EntityActionType ability) { super(ability); }
	}
}
