package com.github.standobyte.jojo.client.ui.utils.tooltip;

import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.util.FormattedCharSequence;

public abstract class MutableTooltipWrapper extends Tooltip {

	public MutableTooltipWrapper() {
		super(CommonComponents.EMPTY, CommonComponents.EMPTY);
	}

	@Override
	public void updateNarration(NarrationElementOutput narrationElementOutput) {
		Tooltip tooltip = updateToolip();
		if (tooltip != null) {
			tooltip.updateNarration(narrationElementOutput);
		}
	}

	@Override
	public List<FormattedCharSequence> toCharSequence(Minecraft minecraft) {
		Tooltip tooltip = updateToolip();
		if (tooltip != null) {
			return tooltip.toCharSequence(minecraft);
		}
		return Collections.emptyList();
	}

	public abstract Tooltip updateToolip();

}
