package rotp.addons.cmoon.effect;

import rotp.core.customobjects.StatusEffectModified;

import rotp.addons.cmoon.CMoonAddon;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class ShiftEffect extends StatusEffectModified {
    public static final int COLOR = 9981180;
    public static final double SPEED_BONUS = 0.2D;

    public ShiftEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOR);
        setUncurable();
        addAttributeModifier(
                Attributes.MOVEMENT_SPEED,
                CMoonAddon.id("gravity_shift_movement_speed"),
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                amplifier -> SPEED_BONUS);
    }
}
