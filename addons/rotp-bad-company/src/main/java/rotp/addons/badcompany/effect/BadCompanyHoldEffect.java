package rotp.addons.badcompany.effect;

import rotp.addons.badcompany.entity.BadCompanyUnitEntity;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public final class BadCompanyHoldEffect extends MobEffect {
    public static final int COLOR = 2324545;

    public BadCompanyHoldEffect() {
        super(MobEffectCategory.NEUTRAL, COLOR);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(
            int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(
            LivingEntity entity, int amplifier) {
        if (entity instanceof BadCompanyUnitEntity unit) {
            unit.holdPosition();
        }
        return true;
    }
}
