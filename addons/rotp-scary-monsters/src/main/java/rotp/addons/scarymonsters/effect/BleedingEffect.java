package rotp.addons.scarymonsters.effect;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public final class BleedingEffect extends MobEffect {
    public BleedingEffect() {
        super(MobEffectCategory.HARMFUL, 0x8B0000);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(
            int duration, int amplifier) {
        return duration % 20 == 0;
    }

    @Override
    public boolean applyEffectTick(
            LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide() && entity.isAlive()) {
            entity.hurt(
                    entity.damageSources().wither(),
                    Math.max(0.5F, entity.getHealth() * 0.01F));
            if (entity.level() instanceof ServerLevel level) {
                level.sendParticles(
                        ParticleTypes.DAMAGE_INDICATOR,
                        entity.getX(),
                        entity.getY() + entity.getBbHeight() * 0.5D,
                        entity.getZ(),
                        8,
                        0.75D, 0.75D, 0.75D,
                        0.1D);
            }
        }
        return true;
    }
}
