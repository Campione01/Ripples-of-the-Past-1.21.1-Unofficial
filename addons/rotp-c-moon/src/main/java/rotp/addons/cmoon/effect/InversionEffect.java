package rotp.addons.cmoon.effect;

import rotp.core.customobjects.StatusEffectModified;
import rotp.core.util.functions.DamageUtil;

import rotp.addons.cmoon.CMoonDamageTypes;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public final class InversionEffect extends StatusEffectModified {
    public static final int COLOR = 65280;

    public InversionEffect() {
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
        if (!entity.level().isClientSide() && entity.isAlive()) {
            int divisor = Math.max(1, amplifier);
            if (entity.tickCount % divisor / 2 == 0) {
                entity.hurt(
                        DamageUtil.make(
                                entity.level(),
                                CMoonDamageTypes.INVERSION),
                        amplifier / 2.0F + 1.0F);
            }
        }
        return true;
    }
}
