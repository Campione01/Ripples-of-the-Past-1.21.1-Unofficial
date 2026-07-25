package com.github.standobyte.jojoimpl.powers.pillarman.abilities;

import com.github.standobyte.jojo.init.ModParticles;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.init.ModStatusEffects;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanMode;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class PillarmanLightFlashDecoyAbility extends PillarmanActionAbility {
	protected static final int HOLD_TO_FIRE_TICKS = 50;

	public PillarmanLightFlashDecoyAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, -1, PillarmanMode.LIGHT, false, 40.0F, 0.0F, 0.0F, 120,
				LightFlashDecoyInstance::new);
		setButtonHoldPhase(ActionPhase.BUTTON_CHARGE);
		setDefaultPhaseLength(ActionPhase.BUTTON_CHARGE, 50);
		setDefaultPhaseLength(ActionPhase.PERFORM, 1);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
	}

	public static class LightFlashDecoyInstance extends EntityActionInstance {
		public LightFlashDecoyInstance(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void onSetPhase(ActionPhase newPhase) {
			if (ability instanceof PillarmanLightFlashDecoyAbility decoyAbility
					&& (newPhase == ActionPhase.BUTTON_CHARGE || newPhase == ActionPhase.PERFORM)) {
				userWalkSpeed = decoyAbility.heldWalkSpeed;
				LivingEntity user = getPowerUser();
				if (user != null) {
					setBladesVisible(user, true);
				}
			}
			else {
				userWalkSpeed = 1.0F;
			}
		}

		@Override
		public void actionTick() {
			if (getPhase() == ActionPhase.BUTTON_CHARGE && level().isClientSide()) {
				LivingEntity user = getPowerUser();
				if (user != null) {
					PillarmanWindCloakAbility.windEffect(user, ModParticles.HAMON_AURA_RAINBOW.get(), 10);
				}
			}
		}

		@Override
		public void actionPerformStart() {
			Level level = level();
			LivingEntity user = getPowerUser();
			if (user == null || !(ability instanceof PillarmanLightFlashDecoyAbility decoyAbility)) {
				return;
			}
			if (level.isClientSide()) {
				PillarmanLightFlashAbility.createFlashEmitter(user, ParticleTypes.FLASH);
			}
			if (!level.isClientSide()) {
				Power<?> context = decoyAbility.getUserPower(user);
				if (!decoyAbility.consumeEnergy(context)) {
					forceStop();
					syncPhaseChanges();
					return;
				}
				user.addEffect(new MobEffectInstance(ModStatusEffects.FULL_INVISIBILITY, 100, 0, false, false, false));
				decoyAbility.setPillarmanFixedCooldown(context, 120);
				if (context != null && context.getCurTypeData() != null) {
					context.getCurTypeData().syncOnUpdate(user);
				}
			}
			level.playSound(null, user, ModSoundEvents.AJA_STONE_BEAM.get(), user.getSoundSource(), 2.0F, 1.0F);
		}

		@Override
		public void onButtonStopHold() {
			if (getPhase() == ActionPhase.BUTTON_CHARGE && getPhaseTick() < HOLD_TO_FIRE_TICKS) {
				forceStop();
				syncPhaseChanges();
			}
		}

		@Override
		public void onActionCleared(EntityActionInstance newAction) {
			LivingEntity user = getPowerUser();
			if (user != null) {
				setBladesVisible(user, false);
			}
			userWalkSpeed = 1.0F;
		}
	}
}
