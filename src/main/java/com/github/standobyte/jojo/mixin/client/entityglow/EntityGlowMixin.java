package com.github.standobyte.jojo.mixin.client.entityglow;

import java.util.Optional;
import java.util.OptionalInt;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.standobyte.jojo.subsystems.entityglow.EntityGlowChannel;

import net.minecraft.world.entity.Entity;

@Mixin(Entity.class)
public class EntityGlowMixin {

	@Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
	private void jojo_ripples$entityGlowChannelIsGlowing(CallbackInfoReturnable<Boolean> ci) {
		if (EntityGlowChannel.currentGlowAny((Entity) (Object) this).isPresent()) {
			ci.setReturnValue(true);
		}
	}

	@Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
	private void jojo_ripples$entityGlowChannelColor(CallbackInfoReturnable<Integer> ci) {
		Optional<OptionalInt> glowColor = EntityGlowChannel.currentGlowAny((Entity) (Object) this);
		if (glowColor.isPresent() && glowColor.get().isPresent()) {
			ci.setReturnValue(glowColor.get().getAsInt());
		}
	}
}
