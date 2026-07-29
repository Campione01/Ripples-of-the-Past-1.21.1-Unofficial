package com.github.standobyte.jojo.mixin.client.render;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.standobyte.jojo.api.client.render.ClientSkyPresentationProviders;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

@Mixin(ClientLevel.class)
public abstract class ClientLevelSkyPresentationMixin {
	@WrapOperation(
			method = {
					"getSkyDarken",
					"getSkyColor",
					"getCloudColor",
					"getStarBrightness"
			},
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/multiplayer/"
							+ "ClientLevel;getTimeOfDay(F)F"))
	private float jojo_ripples$presentSkyTimeOfDay(
			ClientLevel level,
			float partialTick,
			Operation<Float> original) {
		return ClientSkyPresentationProviders.timeOfDay(
				level,
				partialTick,
				original.call(level, partialTick));
	}

	@Inject(
			method = "getSkyDarken",
			at = @At("RETURN"),
			cancellable = true)
	private void jojo_ripples$presentSkyDarken(
			float partialTick,
			CallbackInfoReturnable<Float> cir) {
		ClientLevel level = (ClientLevel) (Object) this;
		cir.setReturnValue(
				ClientSkyPresentationProviders.skyDarken(
						level,
						partialTick,
						cir.getReturnValue()));
	}

	@Inject(
			method = "getStarBrightness",
			at = @At("RETURN"),
			cancellable = true)
	private void jojo_ripples$presentStarBrightness(
			float partialTick,
			CallbackInfoReturnable<Float> cir) {
		ClientLevel level = (ClientLevel) (Object) this;
		cir.setReturnValue(
				ClientSkyPresentationProviders.starBrightness(
						level,
						partialTick,
						cir.getReturnValue()));
	}

	@Inject(
			method = "getSkyColor",
			at = @At("RETURN"),
			cancellable = true)
	private void jojo_ripples$presentSkyColor(
			Vec3 cameraPosition,
			float partialTick,
			CallbackInfoReturnable<Vec3> cir) {
		ClientLevel level = (ClientLevel) (Object) this;
		cir.setReturnValue(ClientSkyPresentationProviders.skyColor(
				level,
				cameraPosition,
				partialTick,
				cir.getReturnValue()));
	}
}
