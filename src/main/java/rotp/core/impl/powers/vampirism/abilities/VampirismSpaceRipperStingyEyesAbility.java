package rotp.core.impl.powers.vampirism.abilities;

import rotp.core.customobjects.entity_projectile.SpaceRipperStingyEyesEntity;
import rotp.core.impl.powers.vampirism.VampirismData;
import rotp.core.impl.powers.vampirism.VampirismState;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class VampirismSpaceRipperStingyEyesAbility extends VampirismActionAbility {
	private static final int HOLD_TO_FIRE_TICKS = 20;

	public VampirismSpaceRipperStingyEyesAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, 1, 20.0F, SpaceRipperInstance::new);
		setDefaultPhaseLength(ActionPhase.WINDUP, HOLD_TO_FIRE_TICKS);
		setDefaultPhaseLength(ActionPhase.PERFORM, SpaceRipperInstance.TICK_DURATION + 1);
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
		private static final float TICK_BLOOD_COST = 20.0F;
		private final SpaceRipperStingyEyesEntity[] lasers = new SpaceRipperStingyEyesEntity[2];
		private boolean lasersAdded;
		private int ticksFired;

		public SpaceRipperInstance(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void onSetPhase(ActionPhase newPhase) {
			// 1.16 charged at full speed (no heldWalkSpeed); only the firing instance walked at 0.3.
			userWalkSpeed = newPhase == ActionPhase.PERFORM ? 0.3F : 1.0F;
		}

		@Override
		protected boolean shouldHoldPhaseAtEnd() {
			return getPhase() == ActionPhase.WINDUP;
		}

		// Only a PERFORM phase that did not come from the key release still has no beams here.
		@Override
		public void actionPerformStart() {
			addLasers();
		}

		// 1.16 onStart
		private void addLasers() {
			Level level = level();
			LivingEntity user = getPowerUser();
			if (level.isClientSide() || user == null || lasersAdded) {
				return;
			}
			lasersAdded = true;
			lasers[0] = new SpaceRipperStingyEyesEntity(level, user, true);
			lasers[1] = new SpaceRipperStingyEyesEntity(level, user, false);
			level.addFreshEntity(lasers[0]);
			level.addFreshEntity(lasers[1]);
		}

		// 1.16 playerTick ran with tick = 0..TICK_DURATION: every one of those ticks took the cost, the last one ended the shot.
		@Override
		public void actionTick() {
			if (getPhase() != ActionPhase.PERFORM || level().isClientSide()) {
				return;
			}
			LivingEntity user = getPowerUser();
			boolean paid = user != null && consumeBlood(user, TICK_BLOOD_COST);
			if (!paid && user != null) {
				emptyBlood(user);
			}
			if (ticksFired >= TICK_DURATION || !paid) {
				// 1.16 ticked the beams before the player, so they were detached after this tick's bound move.
				for (int i = 0; i < lasers.length; i++) {
					if (lasers[i] != null && lasers[i].isAlive()) {
						lasers[i].detachAfterThisTicksMove();
					}
					lasers[i] = null;
				}
				setCooldown(user);
				forceStop();
				return;
			}
			ticksFired++;
		}

		private static void emptyBlood(LivingEntity user) {
			VampirismData data = getVampirismData(user);
			if (data != null) {
				VampirismState.get(user).blood().setCurrent(0.0F);
				data.setBloodLevel(0.0F);
				data.syncOnUpdate(user);
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
				if (getPhaseTick() >= getCurPhaseLength() && canPayForRelease()) {
					setPhaseStart(ActionPhase.PERFORM);
					// 1.16 NonStandAction.onPerform: perform() added the beams, then the action's cost was taken once.
					addLasers();
					LivingEntity user = getPowerUser();
					if (user != null && !level().isClientSide()) {
						consumeBlood(user, TICK_BLOOD_COST);
					}
				}
				else {
					forceStop();
				}
				syncPhaseChanges();
			}
		}

		// 1.16 PowerBaseImpl.stopHeldAction fired only when the requirements, the action's cost among them, still held.
		private boolean canPayForRelease() {
			if (level().isClientSide()) {
				return true;
			}
			LivingEntity user = getPowerUser();
			VampirismData data = getVampirismData(user);
			return user != null && (isUserCreative() || data != null && data.hasBlood(user, TICK_BLOOD_COST));
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
