package rotp.addons.yellowtemperance.effect;

import java.util.Set;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.common.EffectCure;

public final class YellowTemperanceEffect extends MobEffect {
    public YellowTemperanceEffect(
            MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public void fillEffectCures(
            Set<EffectCure> cures,
            MobEffectInstance effectInstance) {
        cures.clear();
    }
}
