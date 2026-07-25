package com.github.standobyte.jojo.mechanics.standarrow.client;

import com.github.standobyte.jojo.client.ui.hud_misc.VanillaHudSprites;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.mechanics.standarrow.StandVirusActualEffect;
import com.github.standobyte.jojo.mechanics.standarrow.StandVirusEffect;
import com.github.standobyte.jojo.mixin.entity_like_player.puppetcontrol.client.GuiAccessor;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT)
public class StandVirusClientHud {
	private static final int XP_BAR_WIDTH = 182;
	private static final int XP_BAR_HEIGHT = 5;
	private static final int XP_GREEN = 0x80FF20;
	private static final int ARROW_XP_GOLD = 0xFFD820;

	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void renderStandVirusXp(RenderGuiLayerEvent.Pre event) {
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		if (player == null || player.isSpectator() || mc.options.hideGui || mc.gameMode == null || !mc.gameMode.hasExperience()) {
			return;
		}

		StandVirusActualEffect effect = StandVirusEffect.getActualVirusEffect(player, false);
		if (effect == null) {
			return;
		}
		int standArrowLevels = effect.getXpLevelsTakenByArrow();
		if (standArrowLevels <= 0) {
			return;
		}

		ResourceLocation layerName = event.getName();
		if (layerName.equals(VanillaGuiLayers.EXPERIENCE_BAR)) {
			event.setCanceled(true);
			renderExperienceBar(event.getGuiGraphics(), player, mc);
		}
		else if (layerName.equals(VanillaGuiLayers.EXPERIENCE_LEVEL)) {
			event.setCanceled(true);
			renderExperienceLevel(event.getGuiGraphics(), player, mc.font, standArrowLevels);
		}
	}

	private static void renderExperienceBar(GuiGraphics guiGraphics, Player player, Minecraft mc) {
		int xpNeeded = player.getXpNeededForNextLevel();
		if (xpNeeded <= 0) {
			return;
		}

		GuiAccessor gui = (GuiAccessor) mc.gui;
		VanillaHudSprites.cacheSpritePaths(gui);
		int xPos = guiGraphics.guiWidth() / 2 - 91;
		int yPos = guiGraphics.guiHeight() - 32 + 3;
		int progress = Mth.clamp((int) (player.experienceProgress * 183.0F), 0, XP_BAR_WIDTH);

		RenderSystem.disableBlend();
		guiGraphics.blitSprite(VanillaHudSprites.EXPERIENCE_BAR_BACKGROUND_SPRITE, xPos, yPos, XP_BAR_WIDTH, XP_BAR_HEIGHT);
		if (progress > 0) {
			guiGraphics.blitSprite(VanillaHudSprites.EXPERIENCE_BAR_PROGRESS_SPRITE, XP_BAR_WIDTH, XP_BAR_HEIGHT, 0, 0, xPos, yPos, progress, XP_BAR_HEIGHT);
		}
		RenderSystem.enableBlend();
	}

	private static void renderExperienceLevel(GuiGraphics guiGraphics, Player player, Font font, int standArrowLevels) {
		String xpLevels = player.experienceLevel > 0 ? player.experienceLevel + " " : "";
		String arrowLevels = "(" + standArrowLevels + ")";

		int width = font.width(xpLevels + arrowLevels);
		int xpNumX = (guiGraphics.guiWidth() - width) / 2;
		int arrowXpNumX = xpNumX + font.width(xpLevels);
		int numberY = guiGraphics.guiHeight() - 31 - 4;

		drawOutlinedString(guiGraphics, font, xpLevels, xpNumX, numberY, XP_GREEN);
		drawOutlinedString(guiGraphics, font, arrowLevels, arrowXpNumX, numberY, ARROW_XP_GOLD);
	}

	private static void drawOutlinedString(GuiGraphics guiGraphics, Font font, String text, int x, int y, int color) {
		if (text.isEmpty()) {
			return;
		}
		guiGraphics.drawString(font, text, x + 1, y, 0, false);
		guiGraphics.drawString(font, text, x - 1, y, 0, false);
		guiGraphics.drawString(font, text, x, y + 1, 0, false);
		guiGraphics.drawString(font, text, x, y - 1, 0, false);
		guiGraphics.drawString(font, text, x, y, color, false);
	}
}
