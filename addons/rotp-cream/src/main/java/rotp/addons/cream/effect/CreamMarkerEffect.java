package rotp.addons.cream.effect;

import rotp.core.customobjects.StatusEffectModified;

import net.minecraft.world.effect.MobEffectCategory;

public final class CreamMarkerEffect extends StatusEffectModified {
    public CreamMarkerEffect(
            MobEffectCategory category, int color) {
        super(category, color);
        setUncurable();
    }
}
