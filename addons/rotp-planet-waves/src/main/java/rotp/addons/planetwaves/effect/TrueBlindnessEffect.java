package rotp.addons.planetwaves.effect;

import rotp.core.customobjects.StatusEffectModified;

import net.minecraft.world.effect.MobEffectCategory;

public final class TrueBlindnessEffect extends StatusEffectModified {
    public static final int COLOR = 0xC75FDA;

    public TrueBlindnessEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
        setUncurable();
    }
}
