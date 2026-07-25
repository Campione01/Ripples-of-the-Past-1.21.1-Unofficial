package com.github.standobyte.jojo.mixin.client.timestop;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.github.standobyte.jojo.client.ClientTimeStopHandler;

import net.minecraft.client.renderer.texture.TextureManager;

@Mixin(TextureManager.class)
public class TextureManagerTimeStopMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void jojo_ripples$cancelTextureTickInTimeStop(CallbackInfo ci) {
		if (ClientTimeStopHandler.shouldFreezeVisualTick()) {
			ci.cancel();
		}
	}
}
