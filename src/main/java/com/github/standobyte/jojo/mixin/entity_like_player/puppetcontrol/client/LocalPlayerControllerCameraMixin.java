package com.github.standobyte.jojo.mixin.entity_like_player.puppetcontrol.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.standobyte.jojo.subsystems.entity_puppetcontrol.client.ClientEntityController;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerControllerCameraMixin {
	@Inject(
			method = "isControlledCamera",
			at = @At("RETURN"),
			cancellable = true)
	private void jojo_ripples$controllerOwnsLocalCamera(
			CallbackInfoReturnable<Boolean> ci) {
		if (ci.getReturnValue()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		ClientEntityController controller =
				ClientEntityController.getInstance();
		if (minecraft.player == (Object) this
				&& controller != null
				&& controller.entity == minecraft.getCameraEntity()
				&& controller.controlsLocalPlayerCamera()) {
			ci.setReturnValue(true);
		}
	}
}
