package com.github.standobyte.jojo.mixin.timestop;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.github.standobyte.jojo.subsystems.timestop.TimeStopState;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

@Mixin(LivingEntity.class)
public abstract class TimeStopFloatTravelMixin {
	@ModifyVariable(method = "travel(Lnet/minecraft/world/phys/Vec3;)V", at = @At("STORE"), ordinal = 0)
	private double jojo_ripples$timeStopFloatTravelGravity(double gravity) {
		if ((Object) this instanceof Player player) {
			return TimeStopState.travelGravityForTimeStopFloat(player, gravity);
		}
		return gravity;
	}
}
