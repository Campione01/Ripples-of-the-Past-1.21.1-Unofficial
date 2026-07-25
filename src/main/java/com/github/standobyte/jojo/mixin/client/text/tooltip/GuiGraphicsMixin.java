package com.github.standobyte.jojo.mixin.client.text.tooltip;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.github.standobyte.jojo.client.ui.utils.tooltip.TooltipParams;

import net.minecraft.client.gui.GuiGraphics;

@Mixin(GuiGraphics.class)
public class GuiGraphicsMixin {

	@Inject(method = "renderTooltipInternal", at = @At("RETURN"))
	public void jojo_ripples$renderTooltipPost(CallbackInfo ci) {
		TooltipParams.renderTooltipPost();
	}
}
