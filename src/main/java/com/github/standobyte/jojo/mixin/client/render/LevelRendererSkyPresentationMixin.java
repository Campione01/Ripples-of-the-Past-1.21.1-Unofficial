package com.github.standobyte.jojo.mixin.client.render;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.github.standobyte.jojo.api.client.render.ClientSkyPresentationProviders;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererSkyPresentationMixin {
	@WrapOperation(
			method = "renderSky",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/multiplayer/"
							+ "ClientLevel;getTimeOfDay(F)F"))
	private float jojo_ripples$presentSkyDomeTime(
			ClientLevel level,
			float partialTick,
			Operation<Float> original) {
		return ClientSkyPresentationProviders.timeOfDay(
				level,
				partialTick,
				original.call(level, partialTick));
	}
}
