package com.github.standobyte.v1_21_4_stuff.missingmethods;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;

public class _Screen {

	public static boolean showsActiveEffects(Screen screen) {
		return screen instanceof EffectRenderingInventoryScreen inventoryScreen && inventoryScreen.canSeeEffects();
	}
	
	public static Font getFont(Screen screen) {
		return Minecraft.getInstance().font;
	}
}
