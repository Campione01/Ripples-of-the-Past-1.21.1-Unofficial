package rotp.addons.whitesnake.effect;

import rotp.core.customobjects.StatusEffectModified;

import rotp.addons.whitesnake.WhitesnakeAddon;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

public final class MeltHeartEffect extends StatusEffectModified {
    public static final int COLOR = 0xE7D6FF;

    public MeltHeartEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
        setUncurable();
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(
            int duration, int amplifier) {
        return duration > 0 && duration % 50 == 0;
    }

    @Override
    public boolean applyEffectTick(
            LivingEntity entity, int amplifier) {
        if (entity.tickCount >= 100
                && entity.level()
                        instanceof ServerLevel serverLevel) {
            MobEffectInstance instance = entity.getEffect(
                    WhitesnakeAddon.MELT_HEART_EFFECT);
            int duration =
                    instance != null ? instance.getDuration() : 0;
            float damage = Math.min(
                    amplifier + 1,
                    Math.round(duration / 20.0F));
            entity.hurt(entity.damageSources().wither(), damage);
            serverLevel.sendParticles(
                    ParticleTypes.CLOUD,
                    entity.getRandomX(1.0D),
                    entity.getRandomY(),
                    entity.getRandomZ(1.0D),
                    1,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D);
        }
        return true;
    }

    public static void applyEffect(
            LivingEntity entity, int amplifier, int duration) {
        if (!entity.level().isClientSide()) {
            duration /= 2;
            amplifier = Math.round(amplifier / 2.0F);
        }
        entity.addEffect(new MobEffectInstance(
                WhitesnakeAddon.MELT_HEART_EFFECT,
                duration,
                amplifier));
    }
}
