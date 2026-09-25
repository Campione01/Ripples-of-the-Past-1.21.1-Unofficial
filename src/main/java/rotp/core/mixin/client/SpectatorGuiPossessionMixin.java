package rotp.core.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import rotp.core.subsystems.entity_possessionv2.LivingComponentPossession;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.spectator.SpectatorGui;

// 1.16 SpectatorGuiMixin: no spectator menu while possessing
@Mixin(SpectatorGui.class)
public class SpectatorGuiPossessionMixin {

	@Inject(method = "renderTooltip", at = @At("HEAD"), cancellable = true)
	private void jojo_ripples$possessionCancelTooltip(GuiGraphics guiGraphics, CallbackInfo ci) {
		if (jojo_ripples$isPossessing()) ci.cancel();
	}

	@Inject(method = "onHotbarSelected", at = @At("HEAD"), cancellable = true)
	private void jojo_ripples$possessionCancelHotbarSelect(int slot, CallbackInfo ci) {
		if (jojo_ripples$isPossessing()) ci.cancel();
	}

	@Inject(method = "onMouseScrolled", at = @At("HEAD"), cancellable = true)
	private void jojo_ripples$possessionCancelScroll(int amount, CallbackInfo ci) {
		if (jojo_ripples$isPossessing()) ci.cancel();
	}

	@Inject(method = "onMouseMiddleClick", at = @At("HEAD"), cancellable = true)
	private void jojo_ripples$possessionCancelMiddleClick(CallbackInfo ci) {
		if (jojo_ripples$isPossessing()) ci.cancel();
	}

	private static boolean jojo_ripples$isPossessing() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && LivingComponentPossession.isPossessingSomeone(mc.player);
	}
}
