package com.github.standobyte.jojo.mechanics;

import com.github.standobyte.jojo.init.ModParticles;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public class HamonShockEffect extends StunEffect {

	public HamonShockEffect(int color) {
		super(color);
		setUncurable();
	}

	@Override
	public boolean applyEffectTick(LivingEntity entity, int amplifier) {
		super.applyEffectTick(entity, amplifier);
		float strength = (amplifier + 1) * 2.5F;
		entity.setYRot(entity.getYRot() + (entity.getRandom().nextFloat() - 0.5F) * strength);
		entity.yHeadRot += (entity.getRandom().nextFloat() - 0.5F) * strength;
		entity.setXRot(entity.getXRot() + (entity.getRandom().nextFloat() - 0.5F) * strength);
		if (entity.level() instanceof ServerLevel serverLevel) {
			Vec3 center = entity.getBoundingBox().getCenter();
			serverLevel.sendParticles(ModParticles.HAMON_SPARK.get(),
					center.x, center.y, center.z, 1,
					entity.getBbWidth() * 0.25D, entity.getBbHeight() * 0.25D, entity.getBbWidth() * 0.25D, 0.03D);
		}
		return true;
	}
}
