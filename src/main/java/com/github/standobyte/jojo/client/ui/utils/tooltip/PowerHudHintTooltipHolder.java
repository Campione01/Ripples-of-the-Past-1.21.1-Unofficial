package com.github.standobyte.jojo.client.ui.utils.tooltip;

import java.time.Duration;

import javax.annotation.Nullable;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetTooltipHolder;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.BelowOrAboveWidgetTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;

/**
 * Copypaste of {@link WidgetTooltipHolder} because fuck me<br>
 * Fuck these asbtractions
 */
public class PowerHudHintTooltipHolder {
	@Nullable
	protected Tooltip tooltip;
	protected Duration delay = Duration.ZERO;
	protected long displayStartTime;
	public boolean wasDisplayed;

	public void setDelay(Duration delay) {
		this.delay = delay;
	}

	public void set(@Nullable Tooltip tooltip) {
		this.tooltip = tooltip;
	}

	@Nullable
	public Tooltip get() {
		return this.tooltip;
	}

	public void refreshTooltipForNextRenderPass(boolean hovering, boolean focused, ScreenRectangle screenRectangle) {
		if (this.tooltip == null) {
			this.wasDisplayed = false;
		} else {
			boolean flag = hovering || focused && Minecraft.getInstance().getLastInputType().isKeyboard();
			if (flag != this.wasDisplayed) {
				if (flag) {
					this.displayStartTime = Util.getMillis();
				}

				this.wasDisplayed = flag;
			}

			if (flag && Util.getMillis() - this.displayStartTime > this.delay.toMillis()) {
				Screen screen = Minecraft.getInstance().screen;
				if (screen != null) {
					screen.setTooltipForNextRenderPass(this.tooltip, this.createTooltipPositioner(screenRectangle, hovering, focused), focused);
				}
			}
		}
	}

	protected /*i ain't using access transformers on that shit*/ ClientTooltipPositioner createTooltipPositioner(ScreenRectangle screenRectangle, boolean hovering, boolean focused) {
		return new BelowOrAboveWidgetTooltipPositioner(screenRectangle);
	}

	public void updateNarration(NarrationElementOutput output) {
		if (this.tooltip != null) {
			this.tooltip.updateNarration(output);
		}
	}
}
