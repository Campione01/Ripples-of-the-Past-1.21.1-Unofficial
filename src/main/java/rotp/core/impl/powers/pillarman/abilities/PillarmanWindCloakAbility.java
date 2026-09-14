package rotp.core.impl.powers.pillarman.abilities;

import rotp.core.init.ModParticles;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.impl.powers.pillarman.PillarmanMode;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public class PillarmanWindCloakAbility extends PillarmanActionAbility {
	private static final int BASE_COOLDOWN = 100;

	public PillarmanWindCloakAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, -1, PillarmanMode.WIND, false, 0.0F, 2.0F, 1.0F, BASE_COOLDOWN,
				WindCloakInstance::new);
		setButtonHoldPhase(ActionPhase.PERFORM);
		setDefaultPhaseLength(ActionPhase.PERFORM, Integer.MAX_VALUE);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
	}

	@Override
	protected int getCooldownAfterHold(Power<?> context, int ticksHeld) {
		return BASE_COOLDOWN;
	}

	public static class WindCloakInstance extends PillarmanHeldActionInstance {
		public WindCloakInstance(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void onSetPhase(ActionPhase newPhase) {
			super.onSetPhase(newPhase);
			if (newPhase == ActionPhase.PERFORM) {
				LivingEntity user = getPowerUser();
				if (user != null) {
					windEffect(user, ModParticles.SANDSTORM.get(), 15);
				}
			}
		}

		@Override
		protected void heldTick(PillarmanActionAbility pillarmanAbility, LivingEntity user, Power<?> context, int ticksHeld) {
			if (user != null && !level().isClientSide()) {
				user.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 5, 0, false, false, true));
				user.addEffect(new MobEffectInstance(ModStatusEffects.SUN_RESISTANCE, 5, 0, false, false, true));
			}
		}

		@Override
		public void onActionCleared(EntityActionInstance newAction) {
			LivingEntity user = getPowerUser();
			super.onActionCleared(newAction);
			if (user != null) {
				windEffect(user, ModParticles.SANDSTORM.get(), 15);
			}
		}
	}

	public static void windEffect(LivingEntity user, ParticleOptions particles, int intensity) {
		if (!user.level().isClientSide()) {
			return;
		}
		RandomSource random = user.getRandom();
		for (int i = 0; i < intensity; i++) {
			Vec3 particlePos = user.position().add(
					(random.nextDouble() - 0.5) * (user.getBbWidth() + 0.5),
					random.nextDouble() * user.getBbHeight(),
					(random.nextDouble() - 0.5) * (user.getBbWidth() + 0.5));
			user.level().addParticle(particles, particlePos.x, particlePos.y, particlePos.z,
					random.nextDouble() - 0.5, random.nextDouble(), random.nextDouble() - 0.5);
		}
	}
}
