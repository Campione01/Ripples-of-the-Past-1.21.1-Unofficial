package rotp.core.mixin.client.timestop;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import rotp.core.client.ClientTimeStopHandler;

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
