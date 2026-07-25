package com.github.standobyte.jojo.client.ui.utils;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class FontUtil {
	
	public static int drawRightAlignedWithBackdrop(GuiGraphics gui, Font font, Component text, int xRight, int y, int color) {
		int width = font.width(text);
		return gui.drawStringWithBackdrop(font, text, xRight - width, y, width, color);
	}
	
	public static int drawWithBackdrop(GuiGraphics gui, Font font, Component text, int x, int y, int color) {
		int width = font.width(text);
		return gui.drawStringWithBackdrop(font, text, x, y, width, color);
	}
	
}
