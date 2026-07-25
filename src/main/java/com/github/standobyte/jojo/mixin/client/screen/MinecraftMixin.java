package com.github.standobyte.jojo.mixin.client.screen;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.github.standobyte.jojo.client.ui.ScreenLetsUseWASD;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Shadow public Screen screen;

	@Inject(method = "tick", at = @At(
			value = "FIELD", 
			target = "overlay",
			opcode = Opcodes.GETFIELD),
			slice = @Slice(
					from = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/DebugScreenOverlay;showDebugScreen()Z"),
					to = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;tick()V")))
	public void jojo_ripples$handleKeybinds(CallbackInfo ci) {
		if (screen != null && ScreenLetsUseWASD.canUseWhenOpen(screen)) {
			handleKeybinds();
		}
	}
	
	@Shadow public abstract void handleKeybinds();
}
