package rotp.addons.heavensdoor.effect;

import rotp.core.customobjects.StatusEffectModified;

import net.minecraft.world.effect.MobEffectCategory;

public final class HeavensDoorStatusEffect
        extends StatusEffectModified {
    public HeavensDoorStatusEffect(int color) {
        super(MobEffectCategory.NEUTRAL, color);
        setUncurable();
    }
}
