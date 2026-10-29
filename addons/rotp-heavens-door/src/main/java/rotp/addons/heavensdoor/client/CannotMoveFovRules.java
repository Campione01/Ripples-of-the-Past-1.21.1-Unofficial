package rotp.addons.heavensdoor.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import rotp.addons.heavensdoor.HeavensDoorAddon;

public final class CannotMoveFovRules {
    private static final ResourceLocation HELD_WALK_SPEED = HeavensDoorAddon.id("cannot_move_walk");

    private CannotMoveFovRules() {}

    public static double movementSpeedForFov(LivingEntity player, double original) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        // Not gated on the effect: its removal reaches the client before the attribute sync, and spectated players never sync effects.
        if (speed == null || !speed.hasModifier(HELD_WALK_SPEED)) {
            return original;
        }
        // A detached copy preserves vanilla operation order/sanitization without restoring live acceleration.
        AttributeInstance visualSpeed = new AttributeInstance(speed.getAttribute(), ignored -> {});
        visualSpeed.replaceFrom(speed);
        visualSpeed.removeModifier(HELD_WALK_SPEED);
        return visualSpeed.getValue();
    }
}
