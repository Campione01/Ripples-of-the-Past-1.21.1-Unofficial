package com.github.standobyte.jojo.client.ui.screen.walkman;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class WalkmanVolumeWheel extends AbstractWidget {
	private static final float FULL_WHEEL_LENGTH = 100.0F;
	private final WalkmanScreen screen;
	private float value;

	public WalkmanVolumeWheel(WalkmanScreen screen, int x, int y, int width, int height) {
		super(x, y, width, height, Component.literal("walkman.volume"));
		this.screen = screen;
	}

	void setValue(float value, boolean notify) {
		value = Mth.clamp(value, 0.0F, 1.0F);
		if (this.value != value) {
			this.value = value;
			if (notify) {
				screen.onVolumeChanged(value);
			}
		}
	}

	float getValue() {
		return value;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (isValidClickButton(button)) {
			setValue(value - (float) dragY / FULL_WHEEL_LENGTH, true);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		setValue(value + (float) scrollY * 0.05F, true);
		return true;
	}

	@Override
	protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		guiGraphics.blit(WalkmanScreen.TEXTURE, getX(), getY(), isHoveredOrFocused() ? 229 : 245, 61 + (int) (value * FULL_WHEEL_LENGTH), width, height);
	}

	@Override
	public void playDownSound(SoundManager soundHandler) {}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
	}
}
