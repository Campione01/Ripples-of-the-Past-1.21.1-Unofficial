package rotp.addons.sexpistols.content.sexpistols;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public final class JoyfulEffect extends MobEffect {
    public static final int COLOR = 0xF4B23A;

    public JoyfulEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOR);
    }
}
