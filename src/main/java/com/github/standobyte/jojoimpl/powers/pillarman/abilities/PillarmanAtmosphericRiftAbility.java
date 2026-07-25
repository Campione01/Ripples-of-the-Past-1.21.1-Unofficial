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
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class PillarmanAtmosphericRiftAbility extends PillarmanActionAbility {
	private static final int HOLD_TO_FIRE_TICKS = 40;

	public PillarmanAtmosphericRiftAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, -1, PillarmanMode.WIND, false, 0.0F, 0.0F, 0.0F, 0,
				AtmosphericRiftInstance::new);
		setButtonHoldPhase(ActionPhase.BUTTON_CHARGE);
		setDefaultPhaseLength(ActionPhase.BUTTON_CHARGE, 40);
		setDefaultPhaseLength(ActionPhase.PERFORM, 999999);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
	}

	public static class AtmosphericRiftInstance extends PillarmanHeldActionInstance {
		public AtmosphericRiftInstance(EntityActionType ability) {
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
			if (level().isClientSide()) {
				LivingEntity user = getPowerUser();
				if (user != null) {
					if (getPhase() == ActionPhase.BUTTON_CHARGE && getPhaseTick() < HOLD_TO_FIRE_TICKS) {
						PillarmanActionAbility.auraEffect(user, ModParticles.HAMON_AURA_GREEN.get(), 3);
					}
					else if (getPhase() == ActionPhase.PERFORM) {
						addBloodParticles(user);
					}
				}
			}
			super.actionTick();
		}

		private void addBloodParticles(LivingEntity user) {
			Level level = level();
			RandomSource random = user.getRandom();
			for (int i = 0; i < 3; i++) {
				Vec3 particlePos = user.position().add(
						(random.nextDouble() - 0.5D) * (user.getBbWidth() + 0.5D),
						random.nextDouble() * user.getBbHeight(),
						(random.nextDouble() - 0.5D) * (user.getBbWidth() + 0.5D));
				level.addParticle(ModParticles.BLOOD.get(), particlePos.x, particlePos.y, particlePos.z,
						(random.nextDouble() - 0.5D) / 2.0D,
						(random.nextDouble() - 0.5D) / 2.0D,
						(random.nextDouble() - 0.5D) / 2.0D);
			}
		}

		@Override
		protected void heldTick(PillarmanActionAbility pillarmanAbility, LivingEntity user, Power<?> context, int performTicks) {
			if (level().isClientSide() || user == null
					|| !(pillarmanAbility instanceof PillarmanAtmosphericRiftAbility riftAbility)) {
				return;
			}
			if (!riftAbility.consumePillarmanEnergy(context, 3.0F)) {
				forceStop();
				syncPhaseChanges();
				return;
			}
			int ticksHeld = HOLD_TO_FIRE_TICKS + performTicks;
			if (ticksHeld >= HOLD_TO_FIRE_TICKS && ticksHeld % 2 == 0) {
				Level level = level();
				PillarmanDivineSandstormEntity sandstormWave = new PillarmanDivineSandstormEntity(level, user, 0)
						.setAtmospheric(true)
						.setRadius(0.5F)
						.setDamage(2.0F)
						.setDuration(60);
				sandstormWave.shootFromRotation(user, 1.75F, 1.0F);
				level.addFreshEntity(sandstormWave);
				level.playSound(null, user.getX(), user.getY(), user.getZ(),
						ModSoundEvents.MAGICIANS_RED_FIRE_BLAST.get(), SoundSource.AMBIENT, 0.1F, 1.0F);
				if (!(user instanceof Player player) || !player.getAbilities().instabuild) {
					user.hurt(level.damageSources().generic(), 1.0F);
				}
			}
		}
	}
}
