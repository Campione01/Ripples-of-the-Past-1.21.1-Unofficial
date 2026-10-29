package rotp.addons.heavensdoor.effect;

import rotp.core.customobjects.StatusEffectModified;

import rotp.addons.heavensdoor.HeavensDoorAddon;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.common.NeoForgeMod;

public final class BookEffect extends StatusEffectModified {
    public BookEffect() {
        super(MobEffectCategory.NEUTRAL, 273);
        setUncurable();
        addAttributeModifier(
                Attributes.ATTACK_DAMAGE,
                HeavensDoorAddon.id("book_attack_damage"),
                -0.2D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(
                Attributes.ATTACK_SPEED,
                HeavensDoorAddon.id("book_attack_speed"),
                -0.2D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(
                Attributes.MOVEMENT_SPEED,
                HeavensDoorAddon.id("book_movement_speed"),
                -0.25D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(
                Attributes.JUMP_STRENGTH,
                HeavensDoorAddon.id("book_jump_strength"),
                -0.2D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(
                NeoForgeMod.SWIM_SPEED,
                HeavensDoorAddon.id("book_swim_speed"),
                -0.2D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }
}
