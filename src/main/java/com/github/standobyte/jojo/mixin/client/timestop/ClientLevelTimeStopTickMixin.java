package com.github.standobyte.jojo.mixin.client.timestop;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.github.standobyte.jojo.subsystems.timestop.TimeStopState;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

@Mixin(ClientLevel.class)
public class ClientLevelTimeStopTickMixin {

	@Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
	private void jojo_ripples$cancelClientEntityTickInTimeStop(Entity entity, CallbackInfo ci) {
		if (TimeStopState.shouldFreezeClientEntity(entity)) {
			ci.cancel();
		}
	}
}
