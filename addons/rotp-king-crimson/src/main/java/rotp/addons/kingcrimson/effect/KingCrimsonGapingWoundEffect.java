package rotp.addons.kingcrimson.effect;

import rotp.core.customobjects.StatusEffectModified;

import net.minecraft.world.effect.MobEffectCategory;

public final class KingCrimsonGapingWoundEffect
        extends StatusEffectModified {
    public static final int COLOR = 0x990000;
    public static final int MAX_AMPLIFIER = 2;

    public KingCrimsonGapingWoundEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
        setUncurable();
    }
}
