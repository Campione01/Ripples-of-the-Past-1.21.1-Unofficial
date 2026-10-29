package rotp.addons.diegosworld.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public final class DiegoBleedingEffect extends MobEffect {
    public static final int COLOR = 0x8A0303;

    public DiegoBleedingEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(
            int duration, int amplifier) {
        return duration % 20 == 0;
    }

    @Override
    public boolean applyEffectTick(
            LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide()) {
            entity.hurt(
                    entity.damageSources().magic(),
                    1.0F + amplifier * 0.5F);
        }
        return true;
    }
}
