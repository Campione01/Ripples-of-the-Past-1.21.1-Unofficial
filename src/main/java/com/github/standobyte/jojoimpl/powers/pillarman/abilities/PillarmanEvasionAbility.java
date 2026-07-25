package com.github.standobyte.jojoimpl.powers.pillarman.abilities;

import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanMode;

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
