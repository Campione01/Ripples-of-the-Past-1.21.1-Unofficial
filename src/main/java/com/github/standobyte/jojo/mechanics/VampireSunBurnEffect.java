package com.github.standobyte.jojo.mechanics;

import com.github.standobyte.jojo.customobjects.StatusEffectApplicable;
import com.github.standobyte.jojo.customobjects.StatusEffectModified;
import com.github.standobyte.jojo.init.ModStatusEffects;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class VampireSunBurnEffect extends StatusEffectModified implements StatusEffectApplicable {

	public VampireSunBurnEffect() {
		super(MobEffectCategory.HARMFUL, MobEffects.WEAKNESS.value().getColor());
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
		return true;
	}

	@Override
	public boolean applyEffectTick(LivingEntity entity, int amplifier) {
		Level level = entity.level();
		if (level.isClientSide()) {
			double x = entity.getX() + (Math.random() - 0.5D) * entity.getBbWidth();
			double y = entity.getY() + Math.random() * entity.getBbHeight();
			double z = entity.getZ() + (Math.random() - 0.5D) * entity.getBbWidth();
			if (amplifier < 2) {
				int count = (amplifier + 1) * 2;
				for (int i = 0; i < count; i++) {
					level.addParticle(ParticleTypes.SMOKE, x, y, z, 0, 0, 0);
				}
			}
			else {
				int count = (Math.min(amplifier, 5) + 1) / 2;
				for (int i = 0; i < count; i++) {
					level.addParticle(ParticleTypes.LARGE_SMOKE, x, y, z, 0, 0, 0);
				}
			}
			if (amplifier >= 4) {
				x = entity.getX() + (Math.random() - 0.5D) * entity.getBbWidth();
				y = entity.getY() + Math.random() * entity.getBbHeight();
				z = entity.getZ() + (Math.random() - 0.5D) * entity.getBbWidth();
				level.addParticle(ParticleTypes.LAVA, x, y, z, 0, 0, 0);
			}
		}
		return true;
	}

	@Override
	public boolean isApplicable(LivingEntity entity) {
		return JojoDefinitions.isUndeadOrVampiric(entity);
	}

	public static float reduceUndeadHealing() {
		return 0;
	}

	public static void giveEffectTo(LivingEntity entity, int duration, int amplifier) {
		entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, amplifier, false, false, true));
		entity.addEffect(new MobEffectInstance(ModStatusEffects.VAMPIRE_SUN_BURN, duration, amplifier, false, false, false));
	}
}
