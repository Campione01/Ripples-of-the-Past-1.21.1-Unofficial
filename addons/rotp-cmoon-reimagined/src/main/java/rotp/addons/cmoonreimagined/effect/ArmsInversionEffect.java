package rotp.addons.cmoonreimagined.effect;

import rotp.core.customobjects.StatusEffectModified;
import rotp.core.util.functions.DamageUtil;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public final class ArmsInversionEffect extends StatusEffectModified {
    public static final int COLOR = 0x9FD562;

    public ArmsInversionEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(
            int duration, int amplifier) {
        return duration % 2 == 0;
    }

    @Override
    public boolean applyEffectTick(
            LivingEntity entity, int amplifier) {
        if (entity.isAlive()
                && entity.level()
                        instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                    ParticleTypes.REVERSE_PORTAL,
                    entity.getX(),
                    entity.getY(),
                    entity.getZ(),
                    1,
                    0.0D,
                    0.0D,
                    0.0D,
                    4.0D);
            // 1.16 used GENERIC: skips armor, spares creative
            DamageUtil.hurtThroughInvulTicks(
                    entity,
                    entity.damageSources().generic(),
                    1.0F);
        }
        return true;
    }
}
