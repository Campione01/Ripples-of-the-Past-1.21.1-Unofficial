package rotp.addons.stickyfingers.effect;

import rotp.core.customobjects.StatusEffectModified;
import rotp.core.init.ModStatusEffects;

import rotp.addons.stickyfingers.StickyFingersPhase1Spec;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public final class ZipWoundsEffect extends StatusEffectModified {
    public static final int COLOR = 0xFFD264;

    public ZipWoundsEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOR);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(
            int duration, int amplifier) {
        int interval =
                StickyFingersPhase1Spec
                        .ZIP_WOUNDS_HEAL_INTERVAL_TICKS
                        >> amplifier;
        return interval <= 0 || duration % interval == 0;
    }

    @Override
    public boolean applyEffectTick(
            LivingEntity entity, int amplifier) {
        if (entity.hasEffect(ModStatusEffects.BLEEDING)) {
            entity.removeEffect(ModStatusEffects.BLEEDING);
        }
        if (entity.getHealth() < entity.getMaxHealth()) {
            entity.heal(
                    StickyFingersPhase1Spec
                            .ZIP_WOUNDS_HEAL_AMOUNT);
        }
        return true;
    }
}
