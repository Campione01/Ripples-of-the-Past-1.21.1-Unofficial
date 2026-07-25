package com.github.standobyte.jojoimpl.powers.zombie.abilities;

import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojoimpl.powers.zombie.ZombieData;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

public class ZombieDisguiseAbility extends ZombieActionAbility {
	private static final int HOLD_TO_FIRE_TICKS = 60;

	public ZombieDisguiseAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, false, 0.0F, DisguiseInstance::new);
		setDefaultPhaseLength(ActionPhase.WINDUP, HOLD_TO_FIRE_TICKS);
		setDefaultPhaseLength(ActionPhase.PERFORM, 1);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
	}

	@Override
	protected float getWindupHoldToFireIndicatorLength() {
		return HOLD_TO_FIRE_TICKS;
	}

	@Override
	public Component getName(Power<?> context) {
		return abilityName(context, isDisguised(context) ? ".disable" : "");
	}

	@Override
	public String getSpriteName(Power<?> context) {
		return isDisguised(context) ? "zombie_disguise_on" : super.getSpriteName(context);
	}

	private static boolean isDisguised(Power<?> context) {
		ZombieData zombie = getZombieData(context);
		return zombie != null && zombie.isDisguiseEnabled();
	}

	public static class DisguiseInstance extends EntityActionInstance {
		public DisguiseInstance(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void actionPerformStart() {
			LivingEntity user = getPowerUser();
			if (user == null || level().isClientSide()) {
				return;
			}
			ZombieData zombie = getZombieData(((ZombieActionAbility) ability).getUserPower(user));
			if (zombie != null) {
				zombie.toggleDisguise(user);
			}
		}

		@Override
		public void onButtonStopHold() {
			if (getPhase() == ActionPhase.WINDUP) {
				forceStop();
			}
		}
	}
}
