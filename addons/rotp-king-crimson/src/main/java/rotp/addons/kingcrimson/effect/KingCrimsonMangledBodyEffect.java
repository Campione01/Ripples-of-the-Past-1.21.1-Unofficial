package rotp.addons.kingcrimson.effect;

import rotp.core.customobjects.StatusEffectModified;

import net.minecraft.world.effect.MobEffectCategory;

public final class KingCrimsonMangledBodyEffect
        extends StatusEffectModified {
    public static final int COLOR = 0x990000;

    public KingCrimsonMangledBodyEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
        setUncurable();
    }
}
