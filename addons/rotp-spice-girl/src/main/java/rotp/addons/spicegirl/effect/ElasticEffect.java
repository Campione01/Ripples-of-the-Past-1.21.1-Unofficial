package rotp.addons.spicegirl.effect;

import rotp.core.customobjects.StatusEffectApplicable;
import rotp.core.customobjects.StatusEffectModified;

import rotp.addons.spicegirl.SpiceGirlAddon;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

public final class ElasticEffect
        extends StatusEffectModified
        implements StatusEffectApplicable {
    public static final int COLOR = 0xB1F971;
    public static final double MOVEMENT_SPEED_MODIFIER = -3.0D;

    public ElasticEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOR);
        addAttributeModifier(
                Attributes.MOVEMENT_SPEED,
                SpiceGirlAddon.id(
                        "bounce_movement_speed"),
                MOVEMENT_SPEED_MODIFIER,
                AttributeModifier.Operation.ADD_VALUE);
    }

    @Override
    public boolean isApplicable(LivingEntity entity) {
        return !(entity instanceof Player player
                && player.getAbilities().instabuild);
    }
}
