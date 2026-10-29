package rotp.addons.kingcrimson.effect;

import rotp.core.customobjects.StatusEffectModified;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public final class KingCrimsonBleedingEffect extends StatusEffectModified {
    public static final int COLOR = 0x990000;

    public KingCrimsonBleedingEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
        setUncurable();
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(
            int duration,
            int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(
            LivingEntity entity,
            int amplifier) {
        if (!entity.level().isClientSide() && entity.isAlive()) {
            int effectiveAmplifier = Math.max(amplifier, 1);
            if (entity.tickCount % effectiveAmplifier == 0) {
                entity.hurt(
                        entity.damageSources().magic(),
                        effectiveAmplifier / 2.0F);
            }
        }
        return true;
    }
}
