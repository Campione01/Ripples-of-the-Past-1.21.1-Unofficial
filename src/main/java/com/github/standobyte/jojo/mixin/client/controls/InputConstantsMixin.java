package com.github.standobyte.jojo.mixin.client.controls;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.github.standobyte.jojo.client.util.ClientCursorUtil;
import com.mojang.blaze3d.platform.InputConstants;

@Mixin(InputConstants.class)
public abstract class InputConstantsMixin {

	@Inject(method = "grabOrReleaseMouse", at = @At("HEAD"), cancellable = true)
	private static void jojo_ripples$suppressAcceptanceCursorGrab(
			long window, int mode, double x, double y, CallbackInfo ci) {
		if (ClientCursorUtil.suppressesNativeCursor()) {
			ci.cancel();
		}
	}
}
