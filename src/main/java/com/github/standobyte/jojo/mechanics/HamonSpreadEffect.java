package com.github.standobyte.jojo.mechanics;

import com.github.standobyte.jojo.customobjects.StatusEffectApplicable;
import com.github.standobyte.jojo.customobjects.StatusEffectModified;
import com.github.standobyte.jojo.init.ModStatusEffects;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

public class HamonSpreadEffect extends StatusEffectModified implements StatusEffectApplicable {

	public HamonSpreadEffect(MobEffectCategory category, int color) {
		super(category, color);
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
		return true;
	}

	@Override
	public boolean applyEffectTick(LivingEntity entity, int amplifier) {
		if (!entity.level().isClientSide() && entity instanceof FlyingMob) {
			double gravity = entity.getAttributeValue(Attributes.GRAVITY);
			Vec3 deltaMovement = entity.getDeltaMovement();
			entity.setDeltaMovement(deltaMovement.x, -gravity * (amplifier + 1) * 0.5D, deltaMovement.z);
		}
		return true;
	}

	@Override
	public boolean isApplicable(LivingEntity entity) {
		return JojoDefinitions.isAffectedByHamon(entity);
	}

	public static float reduceUndeadHealing(MobEffectInstance effectInstance, float healAmount) {
		float multiplier = 1 - (float) Math.min(effectInstance.getAmplifier() + 1, 5) * 0.2F;
		return healAmount * multiplier;
	}

	public static void giveEffectTo(LivingEntity entity, int duration, int amplifier) {
		entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, amplifier, false, false, true));
		entity.addEffect(new MobEffectInstance(ModStatusEffects.HAMON_SPREAD, duration, amplifier, false, false, false));
	}
}
