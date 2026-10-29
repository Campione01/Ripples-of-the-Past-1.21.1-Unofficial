package rotp.addons.cream.effect;

import rotp.core.customobjects.StatusEffectModified;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public final class CreamBleedingEffect extends StatusEffectModified {
    public static final int COLOR = 0xC75A5A;

    public CreamBleedingEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
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
        int effectiveAmplifier = Math.max(1, amplifier);
        if (!entity.level().isClientSide()
                && entity.isAlive()
                && entity.tickCount % effectiveAmplifier == 0) {
            entity.hurt(
                    entity.damageSources().magic(),
                    effectiveAmplifier / 2.0F);
        }
        return true;
    }
}
