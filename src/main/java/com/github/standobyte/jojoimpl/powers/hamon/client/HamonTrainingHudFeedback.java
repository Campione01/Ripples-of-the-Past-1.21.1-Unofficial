package com.github.standobyte.jojoimpl.powers.hamon.client;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.github.standobyte.jojo.client.ui.utils.BlitFloat;
import com.github.standobyte.jojo.client.ui.utils.ElementTransparency;
import com.github.standobyte.jojo.client.ui.utils.FontUtil;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData.Exercise;
import com.github.standobyte.jojoimpl.powers.hamon.HamonStatFeedbackPacket.Stat;
import com.github.standobyte.v1_21_4_stuff.missingmethods.ARGB;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;

public final class HamonTrainingHudFeedback {
	private static final ResourceLocation HAMON_WINDOW = JojoMod.resLoc("textures/gui/hamon_window.png");
	private static final int HUD_X = 10;
	private static final int BOTTOM_MARGIN = 5;
	private static final int STAT_LINE_HEIGHT = 6;
	private static final int EXERCISE_BAR_WIDTH = 92;
	private static final int EXERCISE_BAR_HEIGHT = 7;
	private static final int EXERCISE_LINE_HEIGHT = 9;
	private static final int CENTER_OVERLAY_Y_SHIFT = 68;

	private static final Map<Exercise, ElementTransparency> EXERCISE_BARS = new EnumMap<>(Exercise.class);
	private static final Map<Stat, ElementTransparency> STAT_INCREASES = new EnumMap<>(Stat.class);
	private static final ElementTransparency CENTER_OVERLAY = timer(60, 20);
	private static List<Component> centerOverlayLines = List.of();

	static {
		for (Exercise exercise : Exercise.values()) {
			EXERCISE_BARS.put(exercise, timer(40, 10));
		}
		for (Stat stat : Stat.values()) {
			STAT_INCREASES.put(stat, timer(100, 20));
		}
	}

	private HamonTrainingHudFeedback() {}

	private static ElementTransparency timer(int duration, int fadeOutTicks) {
		ElementTransparency timer = new ElementTransparency(duration, fadeOutTicks);
		timer.ticks = 0;
		return timer;
	}

	public static void onExerciseValueChanged(Exercise exercise) {
		ElementTransparency timer = EXERCISE_BARS.get(exercise);
		if (timer != null) {
			timer.reset();
		}
	}

	public static void onStatIncreased(Stat stat) {
		ElementTransparency timer = STAT_INCREASES.get(stat);
		if (timer != null) {
			timer.reset();
		}
	}

	public static void showExerciseCompletionOverlay(Component firstLine, Component secondLine) {
		centerOverlayLines = List.of(firstLine, secondLine);
		CENTER_OVERLAY.reset();
	}

	public static void render(GuiGraphics guiGraphics, float partialTick) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}

		PlayerPower.getPowerData(mc.player, ModPlayerPowers.HAMON)
				.ifPresent(hamon -> renderBottomLeft(guiGraphics, mc, hamon, partialTick));
		renderCenterOverlay(guiGraphics, mc, partialTick);
	}

	private static void renderBottomLeft(GuiGraphics guiGraphics, Minecraft mc, HamonData hamon, float partialTick) {
		int y = guiGraphics.guiHeight() - BOTTOM_MARGIN;
		for (ElementTransparency timer : EXERCISE_BARS.values()) {
			if (timer.shouldRender()) {
				y -= EXERCISE_LINE_HEIGHT;
			}
		}
		for (ElementTransparency timer : STAT_INCREASES.values()) {
			if (timer.shouldRender()) {
				y -= STAT_LINE_HEIGHT;
			}
		}

		for (Stat stat : Stat.values()) {
			ElementTransparency timer = STAT_INCREASES.get(stat);
			if (timer.shouldRender()) {
				renderStatIncrease(guiGraphics, mc.font, hamon, stat, HUD_X, y, timer.getAlpha(partialTick));
				y += STAT_LINE_HEIGHT;
			}
		}

		for (Exercise exercise : Exercise.values()) {
			ElementTransparency timer = EXERCISE_BARS.get(exercise);
			if (timer.shouldRender()) {
				renderExerciseBar(guiGraphics, mc, hamon, exercise, HUD_X, y, timer.getAlpha(partialTick));
				y += EXERCISE_LINE_HEIGHT;
			}
		}
	}

	private static void renderStatIncrease(GuiGraphics guiGraphics, Font font, HamonData hamon,
			Stat stat, int x, int y, float alpha) {
		MutableComponent statName = Component.translatable("hamon.stat_lvl_increase." + stat.name().toLowerCase(Locale.ROOT))
				.withStyle(Style.EMPTY.withColor(TextColor.fromRgb(statColor(stat))));
		int value = switch (stat) {
		case STRENGTH -> hamon.getStatLevel(HamonData.HamonStat.STRENGTH);
		case CONTROL -> hamon.getStatLevel(HamonData.HamonStat.CONTROL);
		case BREATHING -> (int) hamon.getBreathingLevel();
		};
		Component message = Component.translatable("hamon.stat_lvl_increase", statName, value);

		guiGraphics.pose().pushPose();
		guiGraphics.pose().scale(0.5F, 0.5F, 1.0F);
		FontUtil.drawWithBackdrop(guiGraphics, font, message, x * 2, y * 2, ARGB.white(alpha));
		guiGraphics.pose().popPose();
	}

	private static int statColor(Stat stat) {
		return switch (stat) {
		case STRENGTH -> 0xE21100;
		case CONTROL -> 0x15AF00;
		case BREATHING -> 0x0070D8;
		};
	}

	private static void renderExerciseBar(GuiGraphics guiGraphics, Minecraft mc, HamonData hamon,
			Exercise exercise, int x, int y, float alpha) {
		int ticks = hamon.getExerciseTicks(exercise);
		int maxTicks = Math.max(exercise.getMaxTicks(hamon), 1);
		int tint = ARGB.white(alpha);
		int fillWidth = Math.min(90, 90 * ticks / maxTicks);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		if (fillWidth > 0) {
			blit(guiGraphics, mc, x + 1, y + 1, fillWidth, 5, 93, 250, fillWidth, 5, tint);
		}
		blit(guiGraphics, mc, x, y, EXERCISE_BAR_WIDTH, EXERCISE_BAR_HEIGHT,
				0, 249, EXERCISE_BAR_WIDTH, EXERCISE_BAR_HEIGHT, tint);
		blit(guiGraphics, mc, x - 3, y - 1, 8, 8,
				230, 92 + exercise.ordinal() * 16, 16, 16, tint);
		if (ticks >= maxTicks) {
			blit(guiGraphics, mc, x + 85, y - 1, 8, 8, 230, 188, 16, 16, tint);
		}
		RenderSystem.disableBlend();
	}

	private static void blit(GuiGraphics guiGraphics, Minecraft mc, int x, int y, int width, int height,
			int u, int v, int uWidth, int vHeight, int tint) {
		BlitFloat.blit(guiGraphics.pose(), mc, HAMON_WINDOW,
				x, y, width, height, 1,
				u, v, uWidth, vHeight, 256, 256, tint);
	}

	private static void renderCenterOverlay(GuiGraphics guiGraphics, Minecraft mc, float partialTick) {
		if (!CENTER_OVERLAY.shouldRender() || centerOverlayLines.isEmpty()) {
			return;
		}
		int color = ARGB.white(CENTER_OVERLAY.getAlpha(partialTick));
		if (color >>> 24 <= 8) {
			return;
		}

		Font font = mc.font;
		guiGraphics.pose().pushPose();
		guiGraphics.pose().translate(guiGraphics.guiWidth() / 2.0F,
				guiGraphics.guiHeight() - CENTER_OVERLAY_Y_SHIFT, 0.0F);
		for (int i = centerOverlayLines.size() - 1; i >= 0; i--) {
			Component line = centerOverlayLines.get(i);
			int width = font.width(line);
			guiGraphics.drawStringWithBackdrop(font, line, -width / 2, -4, width, color);
			guiGraphics.pose().translate(0.0F, -13.0F, 0.0F);
		}
		guiGraphics.pose().popPose();
	}
}
