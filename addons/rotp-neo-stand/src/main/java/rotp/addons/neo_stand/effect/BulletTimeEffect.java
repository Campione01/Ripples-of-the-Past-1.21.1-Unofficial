package rotp.addons.neo_stand.effect;

import rotp.core.customobjects.StatusEffectModified;

import net.minecraft.world.effect.MobEffectCategory;

public final class BulletTimeEffect extends StatusEffectModified {
    public static final int COLOR = 0xFFD400;

    public BulletTimeEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOR);
    }
}
