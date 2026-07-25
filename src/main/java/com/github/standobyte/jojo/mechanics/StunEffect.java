package com.github.standobyte.jojo.mechanics;

import com.github.standobyte.jojo.util.reflection.CommonReflection;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;

public class StunEffect extends ImmobilizeEffect {

	public StunEffect(int color) {
		super(color);
		disableCreeperLinger = true;
	}

	@Override
	public boolean applyEffectTick(LivingEntity entity, int amplifier) {
		super.applyEffectTick(entity, amplifier);
		if (entity instanceof Creeper creeper) {
			CommonReflection.setCreeperSwell(creeper, -1);
		}
		return true;
	}

	@Override
	public void onAdded(LivingEntity entity, MobEffectInstance instance, Entity source) {
		if (entity instanceof Mob mob) {
			mob.setNoAi(true);
		}
	}

	@Override
	public void onRemoved(LivingEntity entity, MobEffectInstance instance) {
		if (entity instanceof Mob mob) {
			mob.setNoAi(false);
		}
	}

	@Override
	public boolean isApplicable(LivingEntity entity) {
		return super.isApplicable(entity) && !(entity instanceof Mob mob && mob.isNoAi());
	}
}
