package rotp.core.client.ui.screen_jojomenu;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public interface IJojoMenuTab {
	void renderIcon(GuiGraphics guiGraphics, int x, int y);
	Component getName();
	Tab getTabToOpen();

	// lines under the name in the tab tooltip (1.16 HamonTabGui.additionalTabNameTooltipInfo)
	default List<Component> getTooltipExtraLines() {
		return List.of();
	}

	default boolean onClick(Minecraft mc, @Nullable Screen curScreen) {
		Tab tab = getTabToOpen();
		return tab != null && tab.onTabClick(mc, curScreen);
	}
}
