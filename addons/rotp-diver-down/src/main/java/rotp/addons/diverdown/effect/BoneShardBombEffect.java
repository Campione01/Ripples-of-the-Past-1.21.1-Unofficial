package rotp.addons.diverdown.effect;

import rotp.core.customobjects.StatusEffectModified;

import net.minecraft.world.effect.MobEffectCategory;

public final class BoneShardBombEffect extends StatusEffectModified {
    // 1.16 InitEffects :26
    public static final int COLOR = 0x404040;

    public BoneShardBombEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
        setUncurable();
    }
}
