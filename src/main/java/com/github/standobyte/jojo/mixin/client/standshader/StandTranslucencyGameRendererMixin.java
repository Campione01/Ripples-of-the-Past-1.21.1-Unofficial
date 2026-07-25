package com.github.standobyte.jojo.mixin.client.standshader;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.github.standobyte.jojo.client.shader.ModShaders;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;

@Mixin(GameRenderer.class)
public abstract class StandTranslucencyGameRendererMixin {
	@Inject(method = "renderLevel", at = @At(value = "INVOKE",
			target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V",
			ordinal = 0, shift = At.Shift.BEFORE), require = 1)
	private void jojo_ripples$compositeStandBeforeHandDepthClear(DeltaTracker deltaTracker, CallbackInfo ci) {
		jojo_ripples$compositePendingStand();
	}

	@Inject(method = "renderLevel", at = @At("RETURN"))
	private void jojo_ripples$compositeStandWithoutHandPass(DeltaTracker deltaTracker, CallbackInfo ci) {
		jojo_ripples$compositePendingStand();
	}

	private static void jojo_ripples$compositePendingStand() {
		ModShaders shaders = ModShaders.getInstance();
		if (shaders != null && shaders.standTranslucencyFramebuffer != null) {
			shaders.standTranslucencyFramebuffer.compositeIfPending();
		}
	}
}
