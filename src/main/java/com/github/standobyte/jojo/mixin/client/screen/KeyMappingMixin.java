package com.github.standobyte.jojo.mixin.client.screen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.standobyte.jojo.client.ui.ScreenLetsUseWASD;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

@Mixin(KeyMapping.class)
public class KeyMappingMixin {

	@Inject(method = "releaseAll", at = @At("HEAD"), cancellable = true)
	private static void jojo_ripples$cancelKeyReleaseOnOpen(CallbackInfo ci) {
		if (ScreenLetsUseWASD.canUseWhenOpen(Minecraft.getInstance().screen)) {
			ci.cancel();
		}
	}
	

    @Shadow private IKeyConflictContext keyConflictContext;
	@Inject(method = "getKeyConflictContext", at = @At("HEAD"), cancellable = true)
    public void jojo_ripples$spoofConflictContext(CallbackInfoReturnable<IKeyConflictContext> ci) {
    	if (this.keyConflictContext == KeyConflictContext.IN_GAME) {
    		Screen screen = Minecraft.getInstance().screen;
    		if (screen != null && ScreenLetsUseWASD.canUseWhenOpen(screen)) {
    			ci.setReturnValue(KeyConflictContext.GUI);
    		}
    	}
    }
}
