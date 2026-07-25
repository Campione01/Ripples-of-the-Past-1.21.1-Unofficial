package com.github.standobyte.jojoimpl.powers.vampirism.abilities;

import com.github.standobyte.jojo.customobjects.entity_projectile.SpaceRipperStingyEyesEntity;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class VampirismSpaceRipperStingyEyesAbility extends VampirismActionAbility {
	private static final int HOLD_TO_FIRE_TICKS = 20;

	public VampirismSpaceRipperStingyEyesAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, 1, 20.0F, SpaceRipperInstance::new);
		setDefaultPhaseLength(ActionPhase.WINDUP, HOLD_TO_FIRE_TICKS);
		setDefaultPhaseLength(ActionPhase.PERFORM, 20);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
		setIgnoresPerformerStun();
	}

	@Override
	protected float getWindupHoldToFireIndicatorLength() {
		return HOLD_TO_FIRE_TICKS;
	}

	public static class SpaceRipperInstance extends EntityActionInstance {
		private static final int TICK_DURATION = 20;
		private static final int MAX_COOLDOWN = 50;
		private final SpaceRipperStingyEyesEntity[] lasers = new SpaceRipperStingyEyesEntity[2];
		private int ticksFired;

		public SpaceRipperInstance(EntityActionType ability) {
			super(ability);
			userWalkSpeed = 0.3F;
		}

		@Override
		public void actionPerformStart() {
			Level level = level();
			if (level.isClientSide()) {
				return;
			}
			LivingEntity user = getPowerUser();
			if (user == null) {
				return;
			}
			lasers[0] = new SpaceRipperStingyEyesEntity(level, user, true);
			lasers[1] = new SpaceRipperStingyEyesEntity(level, user, false);
			level.addFreshEntity(lasers[0]);
			level.addFreshEntity(lasers[1]);
		}

		@Override
		public void actionTick() {
			if (getPhase() != ActionPhase.PERFORM || level().isClientSide()) {
				return;
			}
			LivingEntity user = getPowerUser();
			if (user == null || !consumeBlood(user, 20.0F)) {
				detachLasers();
				setCooldown(user);
				forceStop();
				return;
			}
			ticksFired++;
			if (ticksFired >= TICK_DURATION) {
				detachLasers();
				setCooldown(user);
				forceStop();
			}
		}

		@Override
		public void actionPerformEnd() {
			detachLasers();
		}

		@Override
		public void onActionCleared(EntityActionInstance newAction) {
			detachLasers();
		}

		@Override
		public void onButtonStopHold() {
			if (getPhase() == ActionPhase.WINDUP) {
				forceStop();
			}
		}

		private void detachLasers() {
			for (SpaceRipperStingyEyesEntity laser : lasers) {
				if (laser != null && laser.isAlive()) {
					laser.detach();
				}
			}
		}

		private void setCooldown(LivingEntity user) {
			if (user == null || !(ability instanceof VampirismSpaceRipperStingyEyesAbility srse)) {
				return;
			}
			int cooldown = (int) (MAX_COOLDOWN * Math.max(ticksFired, 0) / (float) TICK_DURATION);
			srse.setVampirismCooldown(srse.getUserPower(user), cooldown, MAX_COOLDOWN);
		}
	}
}
