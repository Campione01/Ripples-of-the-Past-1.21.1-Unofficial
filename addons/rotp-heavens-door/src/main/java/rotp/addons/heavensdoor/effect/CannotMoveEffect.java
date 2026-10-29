package rotp.addons.heavensdoor.effect;

import rotp.addons.heavensdoor.HeavensDoorAddon;
import rotp.core.customobjects.StatusEffectApplicable;
import rotp.core.customobjects.StatusEffectModified;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForgeMod;

/** Suppresses voluntary acceleration, not gravity, knockback, view or attacks. */
public final class CannotMoveEffect extends StatusEffectModified implements StatusEffectApplicable {
    public CannotMoveEffect() {
        super(MobEffectCategory.NEUTRAL, 0xFF0500);
        setUncurable();
        addAttributeModifier(Attributes.MOVEMENT_SPEED, HeavensDoorAddon.id("cannot_move_walk"), -1.0D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.FLYING_SPEED, HeavensDoorAddon.id("cannot_move_fly"), -1.0D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(NeoForgeMod.SWIM_SPEED, HeavensDoorAddon.id("cannot_move_swim"), -1.0D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    // The core's STUN, which this command used before, never held a creative player.
    @Override
    public boolean isApplicable(LivingEntity entity) {
        return !(entity instanceof Player player
                && (player.getAbilities().instabuild || player.isSpectator()));
    }
}
