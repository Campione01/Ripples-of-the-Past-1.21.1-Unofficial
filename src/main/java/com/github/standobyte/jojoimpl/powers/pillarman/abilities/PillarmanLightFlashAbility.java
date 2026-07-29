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
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanMode;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class PillarmanLightFlashAbility extends PillarmanActionAbility {
	protected static final int HOLD_TO_FIRE_TICKS = 40;
	private static final int RANGE = 16;

	public PillarmanLightFlashAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, -1, PillarmanMode.LIGHT, false, 25.0F, 0.0F, 0.0F, 80,
				LightFlashInstance::new);
		setButtonHoldPhase(ActionPhase.BUTTON_CHARGE);
		setDefaultPhaseLength(ActionPhase.BUTTON_CHARGE, 40);
		setDefaultPhaseLength(ActionPhase.PERFORM, 1);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
	}

	protected static void createFlashEmitter(LivingEntity user, ParticleOptions particle) {
		if (!user.level().isClientSide()) {
			return;
		}
		int maxEmitterTicks = 19;
		for (int i = maxEmitterTicks; i >= 0; i--) {
			Minecraft.getInstance().particleEngine.createTrackingEmitter(
					user, particle, Math.max(1, maxEmitterTicks - i));
		}
	}

	public static class LightFlashInstance extends EntityActionInstance {
		public LightFlashInstance(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void onSetPhase(ActionPhase newPhase) {
			if (ability instanceof PillarmanLightFlashAbility flashAbility
					&& (newPhase == ActionPhase.BUTTON_CHARGE || newPhase == ActionPhase.PERFORM)) {
				userWalkSpeed = flashAbility.heldWalkSpeed;
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
			if (getPhase() == ActionPhase.BUTTON_CHARGE && level().isClientSide() && getPhaseTick() > 10) {
				LivingEntity user = getPowerUser();
				if (user != null) {
					for (int i = 0; i <= 24; i++) {
						level().addParticle(ModParticles.LIGHT_SPARK.get(), user.getX(), user.getY() + 0.8D, user.getZ(),
								(user.getRandom().nextDouble() - 0.5D) / 4.0D,
								(user.getRandom().nextDouble() - 0.5D) / 4.0D,
								(user.getRandom().nextDouble() - 0.5D) / 4.0D);
					}
				}
			}
		}

		@Override
		public void actionPerformStart() {
			Level level = level();
			LivingEntity user = getPowerUser();
			if (user == null || !(ability instanceof PillarmanLightFlashAbility flashAbility)) {
				return;
			}
			if (level.isClientSide()) {
				createFlashEmitter(user, ModParticles.LIGHT_MODE_FLASH.get());
			}
			if (!level.isClientSide()) {
				Power<?> context = flashAbility.getUserPower(user);
				if (!flashAbility.consumeEnergy(context)) {
					forceStop();
					syncPhaseChanges();
					return;
				}
				Vec3 center = user.getBoundingBox().getCenter();
				AABB area = new AABB(center, center).inflate(RANGE);
				for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area,
						entity -> entity != user && entity.isAlive() && entity.hasLineOfSight(user)
								&& !(entity instanceof StandEntity stand && user.is(stand.getUser())))) {
					int blindnessTicks = entity.distanceToSqr(user) < 25.0D ? 200 : 80;
					entity.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, blindnessTicks, 0, true, true, false));
					if (!(entity instanceof Player) && !(entity instanceof StandEntity)) {
						entity.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 60, 0, true, true, false));
					}
				}
				flashAbility.setPillarmanFixedCooldown(context, 80);
				if (context != null
						&& context.getDataForAbility(
								flashAbility) != null) {
					context.getDataForAbility(flashAbility)
							.syncOnUpdate(user);
				}
			}
			level.playSound(null, user, ModSoundEvents.AJA_STONE_BEAM.get(), user.getSoundSource(),
					(float) (RANGE + 16) / 16.0F, 1.0F);
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
