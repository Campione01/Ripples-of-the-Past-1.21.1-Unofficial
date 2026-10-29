package rotp.addons.stickyfingers.effect;

import rotp.core.customobjects.StatusEffectModified;

import net.minecraft.world.effect.MobEffectCategory;

public final class ZipperEffect extends StatusEffectModified {
    public static final int COLOR = 0xCC9A30;

    public ZipperEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
    }
}
