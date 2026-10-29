package rotp.addons.cream.effect;

import rotp.core.customobjects.StatusEffectModified;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class CreamWeakNeckEffect extends StatusEffectModified {
    public CreamWeakNeckEffect() {
        super(MobEffectCategory.HARMFUL, CreamBleedingEffect.COLOR);
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
        if (!entity.level().isClientSide()
                && !(entity instanceof Player)
                && entity.getRandom().nextFloat()
                        < 0.05F * (amplifier + 1)) {
            entity.hurt(entity.damageSources().generic(), 1.0F);
            entity.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN,
                    100,
                    amplifier + 1,
                    false,
                    false));
            entity.addEffect(new MobEffectInstance(
                    MobEffects.CONFUSION,
                    100,
                    amplifier + 1,
                    false,
                    false));
            entity.setDeltaMovement(
                    entity.getDeltaMovement().add(0.0D, -0.5D, 0.0D));
        }
        return true;
    }
}
