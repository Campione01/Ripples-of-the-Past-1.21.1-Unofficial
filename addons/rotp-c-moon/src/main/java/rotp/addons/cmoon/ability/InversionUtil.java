package rotp.addons.cmoon.ability;

import rotp.core.init.ModStatusEffects;

import rotp.addons.cmoon.CMoonAddon;
import rotp.addons.cmoon.CMoonConfig;
import rotp.addons.cmoon.entity.CMoonStandEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

public final class InversionUtil {
    public static final int STANDARD_DURATION = 75;
    public static final int SHIFT_DURATION_BONUS = 45;
    public static final int HEART_DURATION = 150;

    private InversionUtil() {}

    public static void applyStandard(
            LivingEntity target, CMoonStandEntity stand) {
        int duration = STANDARD_DURATION
                + (stand.isShiftActive()
                        ? SHIFT_DURATION_BONUS
                        : 0);
        target.addEffect(new MobEffectInstance(
                CMoonAddon.CM_INVERSION,
                duration,
                CMoonConfig.DEFAULT_INVERSION_DAMAGE.get(),
                false,
                false,
                true));

        switch (target.getRandom().nextInt(3)) {
            case 0 -> target.addEffect(new MobEffectInstance(
                    ModStatusEffects.MISSHAPEN_ARMS,
                    duration,
                    1,
                    false,
                    false,
                    true));
            case 1 -> target.addEffect(new MobEffectInstance(
                    ModStatusEffects.MISSHAPEN_LEGS,
                    duration,
                    1,
                    false,
                    false,
                    true));
            default -> target.addEffect(new MobEffectInstance(
                    ModStatusEffects.MISSHAPEN_FACE,
                    duration,
                    1,
                    false,
                    false,
                    true));
        }
    }

    public static void applyHeart(LivingEntity target) {
        if (!target.hasEffect(CMoonAddon.CM_INVERSION)) {
            target.addEffect(new MobEffectInstance(
                    CMoonAddon.CM_INVERSION,
                    HEART_DURATION,
                    CMoonConfig
                            .DEFAULT_HEART_INVERSION_DAMAGE.get(),
                    false,
                    false,
                    true));
        }
    }
}
