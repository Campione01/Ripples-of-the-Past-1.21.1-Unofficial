package com.github.standobyte.jojo.mixin.client.text.tooltip;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.github.standobyte.jojo.client.ui.utils.tooltip.TooltipParams;

import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.util.FormattedCharSequence;

@Mixin(ClientTextTooltip.class)
public class ClientTextTooltipMixin {
	@Shadow @Final private FormattedCharSequence text;

	@ModifyArg(method = "renderText", at = @At(value = "INVOKE",
			target = "drawInBatch"))
	public boolean jojo_ripples$renderTextDisableShadow(boolean dropShadow) {
		return dropShadow && TooltipParams.disableShadow() ? false : dropShadow;
	}
}