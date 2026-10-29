package rotp.addons.scr.effect;

import java.util.Set;

import rotp.core.init.ModDamageTypes;
import rotp.core.util.functions.DamageUtil;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.EffectCure;

public final class ChariotVirusEffect extends MobEffect {
    public static final int COLOR = 0xFFEB81;
    public static final int TICK_INTERVAL = 10;

    public ChariotVirusEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(
            int duration, int amplifier) {
        return duration % TICK_INTERVAL == 0;
    }

    @Override
    public boolean applyEffectTick(
            LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide() && entity.isAlive()) {
            float damage = 0.15F + amplifier * 0.2F;
            damage = damage > entity.getHealth()
                    ? 0.001F
                    : damage * 10.0F;
            DamageUtil.hurtThroughInvulTicks(
                    entity,
                    DamageUtil.make(
                            entity.level(),
                            ModDamageTypes.STAND_ARROW_VIRUS),
                    damage);
        }
        return true;
    }

    @Override
    public void fillEffectCures(
            Set<EffectCure> cures,
            MobEffectInstance effectInstance) {
        cures.clear();
    }
}
