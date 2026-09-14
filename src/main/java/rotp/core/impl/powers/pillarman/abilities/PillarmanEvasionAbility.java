package rotp.core.impl.powers.pillarman.abilities;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.impl.powers.pillarman.PillarmanMode;

public class PillarmanEvasionAbility extends PillarmanActionAbility {
	private static final int MAX_HOLD_TICKS = 50;
	private static final int BASE_COOLDOWN = 50;

	public PillarmanEvasionAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, 2, PillarmanMode.NONE, false, 0.0F, 1.5F, 1.0F, BASE_COOLDOWN,
				EvasionInstance::new);
		setButtonHoldPhase(ActionPhase.PERFORM);
		setDefaultPhaseLength(ActionPhase.PERFORM, MAX_HOLD_TICKS);
	}

	@Override
	protected int getCooldownAfterHold(Power<?> context, int ticksHeld) {
		return cooldownFromHoldDuration(BASE_COOLDOWN, ticksHeld, MAX_HOLD_TICKS);
	}

	public static class EvasionInstance extends PillarmanHeldActionInstance {

		public EvasionInstance(EntityActionType ability) {
			super(ability);
		}
	}
}
