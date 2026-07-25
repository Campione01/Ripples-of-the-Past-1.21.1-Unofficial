package com.github.standobyte.jojo.mechanics;

import com.github.standobyte.jojo.client.particle.CustomParticlesHelper;
import com.github.standobyte.jojo.client.sound.HamonSparksLoopSound;
import com.github.standobyte.jojo.customobjects.StatusEffectModified;
import com.github.standobyte.jojo.init.ModParticles;
import com.github.standobyte.jojoimpl.powers.hamon.HamonHypnosisState;

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
