package rotp.addons.ultimate_lifeform.effect;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.core.Holder;

public final class UltimateAttributeEffect extends MobEffect {
    public UltimateAttributeEffect(
            MobEffectCategory category,
            int color,
            Holder<Attribute> attribute,
            ResourceLocation modifierId,
            double amount) {
        super(category, color);
        addAttributeModifier(
                attribute,
                modifierId,
                amount,
                AttributeModifier.Operation.ADD_VALUE);
    }
}
