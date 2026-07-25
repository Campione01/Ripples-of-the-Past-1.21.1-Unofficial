package com.github.standobyte.jojoimpl.powers.pillarman.abilities;

import com.github.standobyte.jojo.init.ModParticles;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanDivineSandstormEntity;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanMode;

import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class PillarmanDivineSandstormAbility extends PillarmanActionAbility {
	protected static final int HOLD_TO_FIRE_TICKS = 40;

	public PillarmanDivineSandstormAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, -1, PillarmanMode.WIND, false, 0.0F, 0.0F, 0.2F, 0,
				DivineSandstormInstance::new);
		setButtonHoldPhase(ActionPhase.BUTTON_CHARGE);
		setDefaultPhaseLength(ActionPhase.BUTTON_CHARGE, 40);
		setDefaultPhaseLength(ActionPhase.PERFORM, 999999);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
	}

	protected PillarmanDivineSandstormEntity createSandstormWave(Level level, LivingEntity user) {
		return new PillarmanDivineSandstormEntity(level, user, 0)
				.setAtmospheric(false)
				.setRadius(1.5F)
				.setDamage(2.0F)
				.setDuration(10);
	}

	protected void shootSandstormWave(PillarmanDivineSandstormEntity sandstormWave, LivingEntity user) {
		sandstormWave.shootFromRotation(user, 0.9F, 2.0F);
	}

	protected float getFireBlastVolume() {
		return 0.2F;
	}

	public static class DivineSandstormInstance extends PillarmanHeldActionInstance {
		public DivineSandstormInstance(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void onSetPhase(ActionPhase newPhase) {
			PillarmanActionAbility pillarmanAbility = pillarmanAbility();
			if (pillarmanAbility != null && (newPhase == ActionPhase.BUTTON_CHARGE || newPhase == ActionPhase.PERFORM)) {
				userWalkSpeed = pillarmanAbility.heldWalkSpeed;
			}
			else {
				userWalkSpeed = 1.0F;
			}
		}

		@Override
		public void onButtonStopHold() {
			if (getPhase() == ActionPhase.BUTTON_CHARGE && getPhaseTick() < HOLD_TO_FIRE_TICKS) {
				forceStop();
				syncPhaseChanges();
				return;
			}
			if (getPhase() == ActionPhase.PERFORM) {
				forceStop();
				syncPhaseChanges();
			}
		}

		@Override
		public void actionTick() {
			if (getPhase() == ActionPhase.BUTTON_CHARGE && level().isClientSide() && getPhaseTick() < HOLD_TO_FIRE_TICKS) {
				LivingEntity user = getPowerUser();
				if (user != null) {
					PillarmanActionAbility.auraEffect(user, ModParticles.HAMON_AURA_GREEN.get(), 6);
				}
			}
			super.actionTick();
		}

		@Override
		protected void heldTick(PillarmanActionAbility pillarmanAbility, LivingEntity user, Power<?> context, int performTicks) {
			if (level().isClientSide() || user == null
					|| !(pillarmanAbility instanceof PillarmanDivineSandstormAbility sandstormAbility)) {
				return;
			}
			if (!sandstormAbility.consumePillarmanEnergy(context, 3.0F)) {
				forceStop();
				syncPhaseChanges();
				return;
			}
			int ticksHeld = HOLD_TO_FIRE_TICKS + performTicks;
			if (ticksHeld >= HOLD_TO_FIRE_TICKS && ticksHeld % 2 == 0) {
				Level level = level();
				PillarmanDivineSandstormEntity sandstormWave = sandstormAbility.createSandstormWave(level, user);
				sandstormAbility.shootSandstormWave(sandstormWave, user);
				level.addFreshEntity(sandstormWave);
				level.playSound(null, user.getX(), user.getY(), user.getZ(),
						ModSoundEvents.MAGICIANS_RED_FIRE_BLAST.get(), SoundSource.AMBIENT,
						sandstormAbility.getFireBlastVolume(), 1.0F);
			}
		}
	}
}
