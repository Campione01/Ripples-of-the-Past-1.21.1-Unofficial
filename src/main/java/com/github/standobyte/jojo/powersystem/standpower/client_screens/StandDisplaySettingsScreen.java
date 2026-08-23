package com.github.standobyte.jojo.powersystem.standpower.client_screens;

import java.util.Locale;

import com.github.standobyte.jojo.client.ui.screen_jojomenu.IJojoMenuScreen;
import com.github.standobyte.jojo.client.ui.screen_jojomenu.Tab;
import com.github.standobyte.jojo.client.ui.screen_jojomenu.TabCategory;
import com.github.standobyte.jojo.client.ui.screen_widgets.HeightScaledSlider;
import com.github.standobyte.jojo.client.ui.utils.BlitFloat;
import com.github.standobyte.jojo.config.client.ClientModSettings;
import com.github.standobyte.jojo.config.client.StandAuraSettings;
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
	private static final int AURA_PARAMETERS_PER_PAGE = 5;
	private final boolean auraPage;
	private final int auraParameterPage;

	public StandDisplaySettingsScreen(TabCategory category, Tab tab) {
		this(category, tab, false, 0);
	}

	private StandDisplaySettingsScreen(
			TabCategory category,
			Tab tab,
			boolean auraPage,
			int auraParameterPage) {
		super(Component.translatable("jojo_ripples.menu.stand.display_settings"));
		this.category = category;
		this.tab = tab;
		this.auraPage = auraPage;
		this.auraParameterPage = Mth.clamp(
				auraParameterPage,
				0,
				auraPageCount() - 1);
	}

	@Override
	protected void init() {
		if (auraPage) {
			initAuraPage();
			return;
		}
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
		addRenderableWidget(Button.builder(
				Component.translatable(
						"jojo_ripples.stand_display.aura_settings"),
				button -> openAuraPage(0))
				.bounds(x, y + 134, controlWidth, 20)
				.build());
		updateControls();
	}

	private void initAuraPage() {
		int x = getWindowX(this) + 20;
		int y = getWindowY(this) + 40;
		int controlWidth = getWindowWidth() - 40;
		int halfWidth = (controlWidth - 4) / 2;
		StandAuraSettings aura = settings().standAura;

		addRenderableWidget(Button.builder(
				auraEnabledMessage(),
				button -> {
					ClientModSettings.edit(
							settings -> settings.standAura.enabled =
									!settings.standAura.enabled,
							false);
					openAuraPage(auraParameterPage);
				})
				.bounds(x, y, halfWidth, 20)
				.build());
		addRenderableWidget(Button.builder(
				auraModeMessage(),
				button -> {
					ClientModSettings.edit(
							settings -> settings.standAura.mode =
									settings.standAura.mode
											== StandAuraSettings.Mode.AUTO
											? StandAuraSettings.Mode.OPEN
											: StandAuraSettings.Mode.AUTO,
							false);
					openAuraPage(auraParameterPage);
				})
				.bounds(x + halfWidth + 4, y, halfWidth, 20)
				.build());

		int firstParameter =
				auraParameterPage * AURA_PARAMETERS_PER_PAGE;
		int lastParameter = Math.min(
				firstParameter + AURA_PARAMETERS_PER_PAGE,
				StandAuraSettings.PARAMETERS.size());
		for (int index = firstParameter;
				index < lastParameter;
				index++) {
			StandAuraSettings.Parameter parameter =
					StandAuraSettings.PARAMETERS.get(index);
			addRenderableWidget(new AuraParameterSlider(
					x,
					y + 30 + (index - firstParameter) * 24,
					controlWidth,
					parameter,
					aura));
		}

		int navigationY = y + 154;
		addRenderableWidget(Button.builder(
				Component.translatable(
						"jojo_ripples.stand_display.display"),
				button -> minecraft.setScreen(
						new StandDisplaySettingsScreen(category, tab)))
				.bounds(x, navigationY, 50, 20)
				.build());
		Button previous = addRenderableWidget(Button.builder(
				Component.literal("<"),
				button -> openAuraPage(auraParameterPage - 1))
				.bounds(x + 54, navigationY, 24, 20)
				.build());
		previous.active = auraParameterPage > 0;
		Button page = addRenderableWidget(Button.builder(
				Component.literal(
						(auraParameterPage + 1)
								+ "/" + auraPageCount()),
				button -> {})
				.bounds(x + 82, navigationY, 38, 20)
				.build());
		page.active = false;
		Button next = addRenderableWidget(Button.builder(
				Component.literal(">"),
				button -> openAuraPage(auraParameterPage + 1))
				.bounds(x + 124, navigationY, 24, 20)
				.build());
		next.active = auraParameterPage + 1 < auraPageCount();
		addRenderableWidget(Button.builder(
				Component.translatable(
						"jojo_ripples.stand_display.aura_reset"),
				button -> {
					ClientModSettings.edit(
							settings -> settings.standAura
									.resetParameters(),
							false);
					openAuraPage(auraParameterPage);
				})
				.bounds(x + 152, navigationY, 38, 20)
				.build());
	}

	private void openAuraPage(int page) {
		minecraft.setScreen(new StandDisplaySettingsScreen(
				category, tab, true, page));
	}

	private Component auraEnabledMessage() {
		return Component.translatable(
				"jojo_ripples.stand_display.aura_enabled",
				Component.translatable(
						settings().standAura.enabled
								? "options.on" : "options.off"));
	}

	private Component auraModeMessage() {
		return Component.translatable(
				"jojo_ripples.stand_display.aura_mode",
				Component.translatable(
						"jojo_ripples.stand_display.aura_mode."
								+ settings().standAura.mode.name()
										.toLowerCase(Locale.ROOT)));
	}

	private static int auraPageCount() {
		return (StandAuraSettings.PARAMETERS.size()
				+ AURA_PARAMETERS_PER_PAGE - 1)
				/ AURA_PARAMETERS_PER_PAGE;
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

	private static class AuraParameterSlider
			extends HeightScaledSlider {
		private final StandAuraSettings.Parameter parameter;

		private AuraParameterSlider(
				int x,
				int y,
				int width,
				StandAuraSettings.Parameter parameter,
				StandAuraSettings settings) {
			super(
					x,
					y,
					width,
					20,
					Component.empty(),
					parameter.normalized(settings));
			this.parameter = parameter;
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			float current = parameter.fromNormalized(value);
			setMessage(Component.translatable(
					"options.generic_value",
					Component.translatable(
							"jojo_ripples.stand_aura.parameter."
									+ parameter.name()),
					Component.literal(String.format(
							Locale.ROOT, "%.3f", current))));
		}

		@Override
		protected void applyValue() {
			float current = parameter.fromNormalized(value);
			ClientModSettings.edit(
					settings -> parameter.set(
							settings.standAura, current),
					false);
		}
	}
}
