package com.github.standobyte.jojo.mrpresident.client;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.github.standobyte.jojo.client.ClientProxy;
import com.github.standobyte.jojo.client.render.item.InventoryItemHighlight;
import com.github.standobyte.jojo.client.ui.hud_misc.VanillaGuiHelper;
import com.github.standobyte.jojo.client.ui.hud_misc.VanillaHudSprites;
import com.github.standobyte.jojo.init.ModBlocks;
import com.github.standobyte.jojo.init.ModItems;
import com.github.standobyte.jojo.init.ModStatusEffects;
import com.github.standobyte.jojo.mixin.entity_like_player.puppetcontrol.client.GuiAccessor;
import com.github.standobyte.jojo.mrpresident.CocoJumboTurtleEntity;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class CocoJumboClientDiscovery {
	private static final double MAX_DISTANCE_SQR = 36.0D;
	private static final double SEARCH_RADIUS = 6.0D;
	private static final Set<UUID> STAND_ARROW_HINTED_COCOS = new HashSet<>();

	private CocoJumboClientDiscovery() {
	}

	public static void tick(Minecraft mc) {
		if (mc.player == null || mc.level == null || mc.isPaused()) {
			return;
		}
		showExitHint(mc);
		AABB searchArea = mc.player.getBoundingBox().inflate(SEARCH_RADIUS);
		for (CocoJumboTurtleEntity coco : mc.level.getEntitiesOfClass(CocoJumboTurtleEntity.class, searchArea)) {
			if (!coco.isAlive() || coco.distanceToSqr(mc.player) >= MAX_DISTANCE_SQR) {
				continue;
			}
			StandPower standPower = StandPower.get(coco);
			if ((standPower == null || !standPower.hasPower()) && !coco.hasEffect(ModStatusEffects.STAND_VIRUS)) {
				InventoryItemHighlight.highlightItem(Items.BOW, 20);
				InventoryItemHighlight.highlightItem(Items.CROSSBOW, 20);
				InventoryItemHighlight.highlightItem(ModItems.STAND_ARROW.get(), 20);
				InventoryItemHighlight.highlightItem(ModItems.STAND_ARROW_BEETLE.get(), 20);
				if (STAND_ARROW_HINTED_COCOS.add(coco.getUUID())) {
					mc.player.displayClientMessage(Component.translatable("coco_jumbo.stand_arrow_hint")
							.withStyle(ChatFormatting.ITALIC), false);
				}
				return;
			}
		}
	}

	private static void showExitHint(Minecraft mc) {
		if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK
				|| !(mc.hitResult instanceof BlockHitResult blockHit)) {
			return;
		}
		if (mc.level.getBlockState(blockHit.getBlockPos()).is(ModBlocks.MR_PRESIDENT_EXIT.get())) {
			ClientProxy.setOverlayMessage(Component.translatable("hint.mr_president_exit"), false);
		}
	}

	public static void renderCarriedSlot(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.player.isSpectator()) {
			return;
		}
		for (Entity passenger : mc.player.getPassengers()) {
			if (CocoJumboTurtleEntity.isCarriedTurtle(passenger, mc.player)) {
				VanillaHudSprites.cacheSpritePaths((GuiAccessor) mc.gui);
				ItemStack turtleItemIcon = new ItemStack(ModItems.COCO_JUMBO_SHELL.get());
				HumanoidArm offHand = mc.player.getMainArm().getOpposite();
				int screenHeight = guiGraphics.guiHeight();
				int halfWidth = guiGraphics.guiWidth() / 2;
				int frameY = screenHeight - 23;
				if (offHand == HumanoidArm.LEFT) {
					guiGraphics.blitSprite(VanillaHudSprites.HOTBAR_OFFHAND_LEFT_SPRITE, halfWidth - 91 - 29, frameY, 29, 24);
				}
				else {
					guiGraphics.blitSprite(VanillaHudSprites.HOTBAR_OFFHAND_RIGHT_SPRITE, halfWidth + 91, frameY, 29, 24);
				}
				int itemX = offHand == HumanoidArm.LEFT ? halfWidth - 91 - 26 : halfWidth + 91 + 10;
				int itemY = screenHeight - 16 - 3;
				VanillaGuiHelper.renderSlot(guiGraphics, itemX, itemY, deltaTracker, mc.player, turtleItemIcon, mc, 22);
				return;
			}
		}
	}
}
