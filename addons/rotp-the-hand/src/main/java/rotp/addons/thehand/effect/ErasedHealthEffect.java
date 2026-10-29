package rotp.addons.thehand.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public final class ErasedHealthEffect extends MobEffect {
    public static final int COLOR = 0xCAF6F4;

    public ErasedHealthEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
    }
}
