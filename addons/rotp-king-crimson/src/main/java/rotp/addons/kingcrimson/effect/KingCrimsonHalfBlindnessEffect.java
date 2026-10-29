package rotp.addons.kingcrimson.effect;

import rotp.core.customobjects.StatusEffectModified;

import net.minecraft.world.effect.MobEffectCategory;

public final class KingCrimsonHalfBlindnessEffect
        extends StatusEffectModified {
    public static final int COLOR = 0x990000;

    public KingCrimsonHalfBlindnessEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
        isUncurable = true;
    }
}
