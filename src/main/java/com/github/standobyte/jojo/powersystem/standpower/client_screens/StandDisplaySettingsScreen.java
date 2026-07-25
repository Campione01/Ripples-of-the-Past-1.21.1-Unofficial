package com.github.standobyte.jojo.powersystem.standpower.client_screens;

import com.github.standobyte.jojo.client.ui.screen_jojomenu.IJojoMenuScreen;
import com.github.standobyte.jojo.client.ui.screen_jojomenu.Tab;
import com.github.standobyte.jojo.client.ui.screen_jojomenu.TabCategory;
import com.github.standobyte.jojo.client.ui.screen_widgets.HeightScaledSlider;
import com.github.standobyte.jojo.client.ui.utils.BlitFloat;
import com.github.standobyte.jojo.config.client.ClientModSettings;
import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class StandDisplaySettingsScreen extends Screen implements IJojoMenuScreen {
	private static final ResourceLocation WINDOW = JojoMod.resLoc("textures/gui/paper_style/empty3.png");

	private final TabCategory category;
	private final Tab tab;
	private Button classicObstructionButton;
	private Button outlineButton;
	private TransparencySlider ownTransparencySlider;
	private TransparencySlider otherTransparencySlider;

	public StandDisplaySettingsScreen(TabCategory category, Tab tab) {
		super(Component.translatable("jojo_ripples.menu.stand.display_settings"));
		this.category = category;
		this.tab = tab;
	}

	@Override
	protected void init() {
		int x = getWindowX(this) + 20;
		int y = getWindowY(this) + 40;
		int controlWidth = getWindowWidth() - 40;

		classicObstructionButton = addRenderableWidget(Button.builder(classicObstructionMessage(), button -> toggleClassicObstruction())
				.bounds(x, y, controlWidth, 20).build());
		outlineButton = addRenderableWidget(Button.builder(outlineMessage(), button -> toggleOutline())
				.bounds(x, y + 32, controlWidth, 20).build());
		ownTransparencySlider = addRenderableWidget(new TransparencySlider(x, y + 70, controlWidth,
				"jojo_ripples.stand_display.own_transparency", true));
		otherTransparencySlider = addRenderableWidget(new TransparencySlider(x, y + 102, controlWidth,
				"jojo_ripples.stand_display.other_transparency", false));
		updateControls();
	}

	private void toggleClassicObstruction() {
		ClientModSettings.edit(settings -> settings.classicStandObstruction = !settings.classicStandObstruction, false);
		updateControls();
	}

	private void toggleOutline() {
		ClientModSettings.edit(settings -> settings.standOutline = !settings.standOutline, false);
		updateControls();
	}

	private void updateControls() {
		classicObstructionButton.setMessage(classicObstructionMessage());
		outlineButton.active = settings().classicStandObstruction;
		outlineButton.setMessage(outlineMessage());
	}

	private Component classicObstructionMessage() {
		return Component.translatable("jojo_ripples.stand_display.classic_obstruction",
				Component.translatable(settings().classicStandObstruction ? "options.on" : "options.off"));
	}

	private Component outlineMessage() {
		return Component.translatable("jojo_ripples.stand_display.classic_outline",
				Component.translatable(settings().standOutline ? "options.on" : "options.off"));
	}

	private static ClientModSettings.Settings settings() {
		return ClientModSettings.getSettingsReadOnly();
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		int x = getWindowX(this);
		int y = getWindowY(this);
		BlitFloat.blit(guiGraphics.pose(), Minecraft.getInstance(), WINDOW,
				x, y, getWindowWidth(), getWindowHeight(), 0,
				0, 0, getWindowWidth(), getWindowHeight(), 256, 256, BlitFloat.NO_TINT);
		guiGraphics.drawCenteredString(font, title, width / 2, y + 16, 0xFF202020);
		super.render(guiGraphics, mouseX, mouseY, partialTick);
		renderTabs(guiGraphics, this);
		renderTabTooltip(guiGraphics, this, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (clickTab(mouseX, mouseY, button, this)) {
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public TabCategory getTabCategory() {
		return category;
	}

	@Override
	public Tab getTab() {
		return tab;
	}

	private static class TransparencySlider extends HeightScaledSlider {
		private final String translationKey;
		private final boolean ownStand;

		private TransparencySlider(int x, int y, int width, String translationKey, boolean ownStand) {
			super(x, y, width, 20, Component.empty(), initialValue(ownStand));
			this.translationKey = translationKey;
			this.ownStand = ownStand;
			updateMessage();
		}

		private static double initialValue(boolean ownStand) {
			float transparency = ownStand ? settings().standTransparency : settings().standOthersTransparency;
			return Mth.clamp(transparency, 0.0F, 100.0F) / 100.0D;
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.translatable(translationKey, Math.round(value * 100.0D)));
		}

		@Override
		protected void applyValue() {
			float transparency = (float) Math.round(value * 100.0D);
			ClientModSettings.edit(settings -> {
				if (ownStand) {
					settings.standTransparency = transparency;
				}
				else {
					settings.standOthersTransparency = transparency;
				}
			}, false);
		}
	}
}
