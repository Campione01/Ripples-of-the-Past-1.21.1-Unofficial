package rotp.addons.weatherreport.effect;

import rotp.core.customobjects.StatusEffectModified;

import rotp.addons.weatherreport.ability.CloudShieldProjectileControl;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public final class CloudShieldEffect extends StatusEffectModified {
    public static final int COLOR = 0x404040;

    public CloudShieldEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOR);
        setUncurable();
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(
            int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(
            LivingEntity entity, int amplifier) {
        if (entity.level() instanceof ServerLevel serverLevel) {
            CloudShieldProjectileControl.redirectGrantedShield(
                    serverLevel, entity);
            serverLevel.sendParticles(
                    ParticleTypes.CLOUD,
                    entity.getX(),
                    entity.getY(0.5D),
                    entity.getZ(),
                    2,
                    entity.getBbWidth() * 0.6D,
                    entity.getBbHeight() * 0.4D,
                    entity.getBbWidth() * 0.6D,
                    0.01D);
        }
        return true;
    }
}
