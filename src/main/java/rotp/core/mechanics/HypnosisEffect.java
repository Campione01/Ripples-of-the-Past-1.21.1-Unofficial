package rotp.core.mechanics;

import rotp.core.client.particle.CustomParticlesHelper;
import rotp.core.client.sound.HamonSparksLoopSound;
import rotp.core.customobjects.StatusEffectModified;
import rotp.core.init.ModParticles;
import rotp.core.impl.powers.hamon.HamonHypnosisState;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

public class HypnosisEffect extends StatusEffectModified {
	public HypnosisEffect(int color) {
		super(MobEffectCategory.HARMFUL, color);
		setUncurable();
	}

	public static void hypnotizeEntity(LivingEntity target, LivingEntity hypnotizer, int duration) {
		HamonHypnosisState.get(target).hypnotizeEntity(hypnotizer, duration);
	}

	@Override
	public void onRemoved(LivingEntity entity, MobEffectInstance instance) {
		HamonHypnosisState.get(entity).relieveHypnosis();
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
		return true;
	}

	@Override
	public boolean applyEffectTick(LivingEntity entity, int amplifier) {
		if (entity.level().isClientSide() && entity.getRandom().nextFloat() < 0.05F) {
			HamonSparksLoopSound.playSparkSound(entity, entity.getBoundingBox().getCenter(), 1.0F, true);
			entity.level().addParticle(ModParticles.HAMON_SPARK.get(),
					entity.getRandomX(0.5D), entity.getRandomY(), entity.getRandomZ(0.5D), 0.0D, 0.0D, 0.0D);
			CustomParticlesHelper.createHamonSparkParticles(entity,
					entity.getRandomX(0.5D), entity.getRandomY(), entity.getRandomZ(0.5D), 1);
		}
		return true;
	}
}
