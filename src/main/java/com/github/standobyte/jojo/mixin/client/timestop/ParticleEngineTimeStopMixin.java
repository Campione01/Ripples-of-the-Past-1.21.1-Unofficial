package com.github.standobyte.jojo.mixin.client.timestop;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.github.standobyte.jojo.client.ClientTimeStopHandler;

import net.minecraft.client.particle.ParticleEngine;

@Mixin(ParticleEngine.class)
public class ParticleEngineTimeStopMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void jojo_ripples$cancelParticleTickInTimeStop(CallbackInfo ci) {
		if (ClientTimeStopHandler.shouldFreezeVisualTick()) {
			ci.cancel();
		}
	}

	@ModifyVariable(
			method = "render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;Ljava/util/function/Predicate;)V",
			at = @At("HEAD"),
			argsOnly = true,
			ordinal = 0)
	private float jojo_ripples$freezeParticlePartialTick(float partialTick) {
		return ClientTimeStopHandler.getConstantWorldPartialTick(partialTick);
	}
}
