package com.github.standobyte.jojoimpl.powers.hamon.client;

import java.util.List;

import com.github.standobyte.jojo.client.ClientTickHandler;
import com.github.standobyte.jojo.client.ui.hud_power.Bars;
import com.github.standobyte.jojo.client.ui.screen_jojomenu.PlaceholderScreen;
import com.github.standobyte.jojo.client.ui.screen_jojomenu.Tab;
import com.github.standobyte.jojo.client.ui.screen_jojomenu.TabCategory;
import com.github.standobyte.jojo.client.ui.utils.BlitFloat;
import com.github.standobyte.jojo.client.ui.utils.Scrolling;
import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

public class HamonIntroScreen extends PlaceholderScreen {
	private static final ResourceLocation HAMON_WINDOW = JojoMod.resLoc("textures/gui/hamon_window.png");
	private static final ResourceLocation HAMON_BREATH_ICON = JojoMod.resLoc("textures/ability/hamon_breath.png");
	private static final int CONTENT_X = 14;
	private static final int CONTENT_Y = 43;
	private static final int CONTENT_WIDTH = 202;
	private static final int CONTENT_HEIGHT = 168;
	private static final int HAMON_COLOR = 0xFFFF00;

	private final Scrolling scrolling = new Scrolling(CONTENT_HEIGHT, 0);
	private boolean showStability;
	private int stabilityToggleContentY = -1;

	public HamonIntroScreen(Component title, TabCategory category, Tab tab) {
		super(title, category, tab, HAMON_WINDOW);
	}

	@Override
	public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		super.render(gui, mouseX, mouseY, partialTick);
		int windowX = getWindowX(this);
		int windowY = getWindowY(this);
		int x = windowX + CONTENT_X;
		int top = windowY + CONTENT_Y;
		int y = top;

		gui.drawString(font, Component.translatable("hamon.intro.tab"), windowX + 16, windowY + 18, 0xFFFFFFFF, false);
		scrolling.pushOffsetScissor(gui, top, x, x + CONTENT_WIDTH);

		gui.drawString(font, Component.translatable("hamon.intro.about.name"), x, y, 0xFFFFFFFF, false);
		y = drawWrapped(gui, Component.translatable("hamon.intro.about.text"), x + 3, y + 12, CONTENT_WIDTH - 6, 0xFFFFFFFF) + 8;

		gui.drawString(font, Component.translatable("hamon.intro.breath.name"), x, y, 0xFFFFFFFF, false);
		y = drawWrapped(gui, Component.translatable("hamon.intro.breath.text1",
				Component.translatable("hamon.intro.breath.text1.underlined").withStyle(ChatFormatting.UNDERLINE)),
				x + 3, y + 12, CONTENT_WIDTH - 6, 0xFFFFFFFF) + 4;
		Bars.renderHorizontalBarWithTranslucent(gui.pose(), x, y, 0.0F, 1.0F,
				Bars.BAR_HORIZONTAL_FILL, HAMON_COLOR, 1.0F);
		y += 14;

		y = drawWrapped(gui, Component.translatable("hamon.intro.breath.text2",
				Component.translatable("hamon.intro.breath.text2.underlined").withStyle(ChatFormatting.UNDERLINE)),
				x + 3, y, CONTENT_WIDTH - 6, 0xFFFFFFFF);
		y = drawWrapped(gui, Component.translatable("hamon.intro.breath.text3",
				Component.translatable("hamon.intro.breath.text3.underlined").withStyle(ChatFormatting.UNDERLINE)),
				x + 3, y + 2, CONTENT_WIDTH - 6, 0xFFFFFFFF) + 4;
		renderBreathAbilityIcon(gui, x + 90, y);
		y += 24;

		float ticks = (ClientTickHandler.tickCount + partialTick) % 100.0F;
		float energyFill = Mth.clamp((ticks - 20.0F) / 60.0F, 0.0F, 1.0F);
		Bars.renderHorizontalBarWithTranslucent(gui.pose(), x, y, energyFill, 1.0F,
				Bars.BAR_HORIZONTAL_FILL, HAMON_COLOR, 1.0F);
		y += 16;

		stabilityToggleContentY = y - top;
		renderStabilityToggle(gui, x, y);
		y += 14;
		if (showStability) {
			y = drawWrapped(gui, Component.translatable("hamon.intro.breath.text4",
					Component.translatable("hamon.intro.breath.text4.underlined").withStyle(ChatFormatting.UNDERLINE)),
					x + 3, y, CONTENT_WIDTH - 6, 0xFFFFFFFF) + 4;

			float slowTicks = (ClientTickHandler.tickCount + partialTick) % 750.0F;
			float stabilityFill = 0.4F + 0.6F * slowTicks / 720.0F;
			float slowEnergyFill = Mth.clamp((slowTicks - 20.0F) / 60.0F, 0.0F, stabilityFill);
			Bars.renderHorizontalBarWithTranslucent(gui.pose(), x, y, slowEnergyFill, stabilityFill,
					Bars.BAR_HORIZONTAL_FILL, HAMON_COLOR, 1.0F);
			y += 14;
			y = drawWrapped(gui, Component.translatable("hamon.intro.breath.text5"),
					x + 3, y, CONTENT_WIDTH - 6, 0xFFFFFFFF) + 6;
		}

		y = drawWrapped(gui, Component.translatable("hamon.intro.stats_transition"),
				x + 3, y, CONTENT_WIDTH - 6, 0xFFFFFFFF) + 10;
		scrolling.setContentsHeight(y - top);
		scrolling.pop(gui);
		renderScrollBar(gui, windowX, top);
		renderTabTooltip(gui, this, mouseX, mouseY);
	}

	private int drawWrapped(GuiGraphics gui, Component text, int x, int y, int width, int color) {
		for (FormattedCharSequence line : font.split(text, width)) {
			gui.drawString(font, line, x, y, color, false);
			y += 10;
		}
		return y;
	}

	private void renderBreathAbilityIcon(GuiGraphics gui, int x, int y) {
		gui.fill(x - 2, y - 2, x + 18, y + 18, 0xFF101010);
		gui.fill(x - 1, y - 1, x + 17, y + 17, 0xFF8A8A8A);
		BlitFloat.blit(gui.pose(), Minecraft.getInstance(), HAMON_BREATH_ICON,
				x, y, 16, 16, 0, BlitFloat.NO_TINT);
	}

	private void renderStabilityToggle(GuiGraphics gui, int x, int y) {
		gui.fill(x, y, x + 10, y + 10, 0xFF181818);
		gui.drawCenteredString(font, showStability ? "-" : "+", x + 5, y + 1, 0xFFFFFFFF);
		gui.drawString(font, Component.translatable("hamon.intro.breath.stability_hidden")
				.withStyle(ChatFormatting.ITALIC), x + 14, y + 1, 0xFFFFFFFF, false);
	}

	private void renderScrollBar(GuiGraphics gui, int windowX, int top) {
		int barX = windowX + 219;
		gui.fill(barX, top, barX + 2, top + CONTENT_HEIGHT, 0x33000000);
		int[] bounds = scrolling.getScrollBarBounds(0, 12);
		if (bounds != null) {
			gui.fill(barX, top + bounds[0], barX + 2, top + bounds[1], 0xFFFFFFFF);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (clickTab(mouseX, mouseY, button, this)) {
			return true;
		}
		if (button == 0) {
			int x = getWindowX(this) + CONTENT_X;
			int top = getWindowY(this) + CONTENT_Y;
			int contentY = scrolling.getYHovered(top, (int) mouseY);
			if (mouseX >= x && mouseX < x + CONTENT_WIDTH
					&& contentY >= stabilityToggleContentY && contentY < stabilityToggleContentY + 12) {
				showStability = !showStability;
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int x = getWindowX(this) + CONTENT_X;
		int y = getWindowY(this) + CONTENT_Y;
		if (mouseX >= x && mouseX < x + CONTENT_WIDTH
				&& mouseY >= y && mouseY < y + CONTENT_HEIGHT) {
			scrolling.scroll(scrollY);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}
}
