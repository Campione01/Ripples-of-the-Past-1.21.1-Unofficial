package rotp.addons.diverdown.effect;

import rotp.core.customobjects.StatusEffectModified;

import net.minecraft.world.effect.MobEffectCategory;

public final class SpringLegsEffect extends StatusEffectModified {
    // 1.16 InitEffects :25
    public static final int COLOR = 0x404040;

    public SpringLegsEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
        setUncurable();
    }
}
