package com.github.standobyte.jojo.client.ui.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT)
public class DynamicButtonVisibility {

	/* I wish I could instead just override AbstractWidget#render, but it's final, 
	 * and all the button rendering is in AbstractWidget#visible if check,
	 * so once I set it to false, I can't unset it that way.
	 * Therefore this dogshit exists now.
	 */
	public static void add(Screen screen, AbstractWidget button, BooleanSupplier visible) {
		buttonRecords.add(new ButtonRecord(screen, button, visible));
	}
	
	protected static record ButtonRecord(Screen screen, AbstractWidget button, BooleanSupplier visible) {}
	
	protected static List<ButtonRecord> buttonRecords = new ArrayList<>();

	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onScreenRender(ScreenEvent.Render.Pre event) {
		Screen curScreen = event.getScreen();
		var buttonsIter = buttonRecords.iterator();
		while (buttonsIter.hasNext()) {
			ButtonRecord record = buttonsIter.next();
			if (record.screen == curScreen) {
				record.button.visible = record.visible.getAsBoolean();
			}
			else {
				buttonsIter.remove();
			}
		}
	}
	
}
