package rotp.addons.stonefree.effect;

import rotp.core.customobjects.StatusEffectModified;

import rotp.addons.stonefree.StoneFreeAddon;
import rotp.addons.stonefree.StoneFreeServices;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.common.NeoForgeMod;

public final class StringDecompositionEffect
        extends StatusEffectModified {
    public static final int COLOR = 0x80DEF7;

    public StringDecompositionEffect() {
        super(MobEffectCategory.NEUTRAL, COLOR);
        setUncurable();
        addAttributeModifier(
                Attributes.MAX_HEALTH,
                StoneFreeAddon.id(
                        "string_decomposition_max_health"),
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                amplifier -> -0.5D * (amplifier + 1));
        addAttributeModifier(
                Attributes.ATTACK_SPEED,
                StoneFreeAddon.id(
                        "string_decomposition_attack_speed"),
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                amplifier -> -0.05D * (amplifier + 1));
        addAttributeModifier(
                Attributes.MOVEMENT_SPEED,
                StoneFreeAddon.id(
                        "string_decomposition_movement_speed"),
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                amplifier -> -0.025D * (amplifier + 1));
        addAttributeModifier(
                NeoForgeMod.SWIM_SPEED,
                StoneFreeAddon.id(
                        "string_decomposition_swim_speed"),
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                amplifier -> -0.025D * (amplifier + 1));
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(
            int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(
            LivingEntity entity, int amplifier) {
        if (!StoneFreeServices.isStoneFreeUser(entity)) {
            entity.removeEffect(
                    StoneFreeAddon.STRING_DECOMPOSITION);
        }
        return true;
    }
}
