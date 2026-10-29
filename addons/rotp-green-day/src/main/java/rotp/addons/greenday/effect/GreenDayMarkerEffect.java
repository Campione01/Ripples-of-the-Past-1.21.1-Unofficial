package rotp.addons.greenday.effect;

import rotp.core.customobjects.StatusEffectModified;

import net.minecraft.world.effect.MobEffectCategory;

public final class GreenDayMarkerEffect extends StatusEffectModified {
    public GreenDayMarkerEffect(
            MobEffectCategory category, int color) {
        super(category, color);
        setUncurable();
    }
}
