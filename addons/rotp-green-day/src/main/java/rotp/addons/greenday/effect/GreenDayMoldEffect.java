package rotp.addons.greenday.effect;

import rotp.core.customobjects.StatusEffectModified;

import rotp.addons.greenday.state.GreenDayMoldState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public final class GreenDayMoldEffect extends StatusEffectModified {
    public static final int COLOR = 0x465D58;

    public GreenDayMoldEffect() {
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
        if (entity.level() instanceof ServerLevel serverLevel) {
            GreenDayMoldState.get(entity)
                    .recordMoldTick(
                            serverLevel, entity, amplifier);
        }
        return true;
    }
}
