package rotp.addons.weatherreport.effect;

import rotp.core.util.functions.DamageUtil;

import rotp.addons.weatherreport.WeatherReportBloodEvents;
import rotp.addons.weatherreport.WeatherReportDamageTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Compatibility owner for the donor-registered {@code rotp_wr:bleeding}
 * effect. Reachable Weather Report attacks continue to use the core effect.
 */
public final class LegacyBleedingEffect extends MobEffect {
    public static final int COLOR = 0x9A0000;

    public LegacyBleedingEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOR);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(
            int duration, int amplifier) {
        int interval = amplifier >= 6 ? 0 : 50 >> amplifier;
        return interval <= 0 || duration % interval == 0;
    }

    @Override
    public boolean applyEffectTick(
            LivingEntity entity, int amplifier) {
        if (!(entity.level() instanceof ServerLevel level)
                || !entity.isAlive()) {
            return true;
        }

        entity.hurt(
                DamageUtil.make(
                        level,
                        WeatherReportDamageTypes.BLEEDING),
                amplifier / 2.0F);

        RandomSource random = entity.getRandom();
        double spread =
                amplifier * (random.nextDouble() - 0.5D);
        for (int i = 0; i < amplifier; i++) {
            WeatherReportBloodEvents.emitBloodDroplet(
                    level, entity, spread, random);
        }
        return true;
    }
}
