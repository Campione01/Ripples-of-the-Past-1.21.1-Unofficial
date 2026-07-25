package com.github.standobyte.jojo.subsystems.soul.client;

import com.github.standobyte.jojo.client.ClientPowerCache;
import com.github.standobyte.jojo.client.ClientProxy;
import com.github.standobyte.jojo.client.util.functions.ClientUtil;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.network.c2s.ClRemovePlayerSoulEntityPacket;
import com.github.standobyte.jojo.network.c2s.ClSoulRotationPacket;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.subsystems.soul.SoulEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT)
public final class ControllerSoul {
	private static final ResourceLocation SOUL_BAR_BACKGROUND = JojoMod.resLoc("textures/hud/soul_bar_background.png");
	private static final ResourceLocation SOUL_BAR_PROGRESS = JojoMod.resLoc("textures/hud/soul_bar_progress.png");
	private static final int SOUL_BAR_WIDTH = 182;
	private static final int SOUL_BAR_HEIGHT = 5;
	private static SoulEntity playerSoulEntity;
	private static boolean firstDeathFrame;
	private static boolean soulEntityWaiting;
	private static boolean jumpWasDown;

	private ControllerSoul() {}

	@SubscribeEvent
	public static void tick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			playerSoulEntity = null;
			firstDeathFrame = false;
			soulEntityWaiting = false;
			jumpWasDown = false;
			return;
		}

		updatePlayerSoul(mc);
		boolean soulCamera = isCameraEntityPlayerSoul();
		if (mc.player.isDeadOrDying()) {
			if (soulEntityWaiting && playerSoulEntity != null) {
				soulEntityWaiting = false;
			}
			if (soulEntityWaiting || soulCamera) {
				mc.player.deathTime = Math.min(mc.player.deathTime, 18);
				ClientProxy.setOverlayMessage(Component.translatable(
						"jojo_ripples.message.skip_soul_ascension", Component.keybind("key.jump")), false);
			}
		}
		else {
			if (!firstDeathFrame) {
				firstDeathFrame = true;
			}
			soulEntityWaiting = false;
		}

		if (soulCamera) {
			mc.player.deathTime = Math.min(mc.player.deathTime, 18);
			ClientProxy.setOverlayMessage(Component.translatable(
					"jojo_ripples.message.skip_soul_ascension", Component.keybind("key.jump")), false);
			PacketDistributor.sendToServer(new ClSoulRotationPacket(
					playerSoulEntity.getId(), playerSoulEntity.getYRot(), playerSoulEntity.getXRot()));
		}

		if (soulCamera || soulEntityWaiting) {
			boolean jumpDown = mc.options.keyJump.isDown();
			if (jumpDown && !jumpWasDown) {
				skipAscension();
			}
			jumpWasDown = jumpDown;
		}
		else {
			jumpWasDown = false;
		}
	}

	private static void updatePlayerSoul(Minecraft mc) {
		Entity cameraEntity = mc.getCameraEntity();
		if (cameraEntity instanceof SoulEntity soulEntity
				&& soulEntity.getOriginEntity() == mc.player
				&& !mc.player.isSpectator()) {
			playerSoulEntity = soulEntity;
		}

		if (playerSoulEntity != null) {
			if (!playerSoulEntity.isAlive() || !mc.player.isDeadOrDying() || mc.player.isSpectator()) {
				if (cameraEntity == playerSoulEntity) {
					ClientUtil.setCameraEntityPreventShaderSwitch(mc.player);
				}
				playerSoulEntity = null;
				return;
			}
			if (cameraEntity != playerSoulEntity) {
				ClientUtil.setCameraEntityPreventShaderSwitch(playerSoulEntity);
			}
		}
	}

	public static boolean isCameraEntityPlayerSoul() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null
				&& playerSoulEntity != null
				&& playerSoulEntity.isAlive()
				&& playerSoulEntity == mc.getCameraEntity()
				&& !mc.player.isSpectator();
	}

	private static void skipAscension() {
		if (isCameraEntityPlayerSoul()) {
			PacketDistributor.sendToServer(new ClRemovePlayerSoulEntityPacket(playerSoulEntity.getId()));
			playerSoulEntity.skipAscension();
		}
		else if (soulEntityWaiting) {
			soulEntityWaiting = false;
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void cancelHandsRender(RenderHandEvent event) {
		if (isCameraEntityPlayerSoul()) {
			event.setCanceled(true);
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void cancelAttackOrInteraction(InteractionKeyMappingTriggered event) {
		if ((event.isAttack() || event.isUseItem()) && isCameraEntityPlayerSoul()) {
			event.setCanceled(true);
			event.setSwingHand(false);
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void renderSoulTimer(RenderGuiLayerEvent.Pre event) {
		if (!isCameraEntityPlayerSoul()) {
			return;
		}
		ResourceLocation layerName = event.getName();
		if (layerName.equals(VanillaGuiLayers.EXPERIENCE_LEVEL)) {
			event.setCanceled(true);
			return;
		}
		if (layerName.equals(VanillaGuiLayers.EXPERIENCE_BAR)) {
			event.setCanceled(true);
			GuiGraphics guiGraphics = event.getGuiGraphics();
			int xPos = guiGraphics.guiWidth() / 2 - 91;
			int yPos = guiGraphics.guiHeight() - 32 + 3;
			int fill = Mth.clamp((int) ((float) playerSoulEntity.getLifeSpan() / (float) playerSoulEntity.getInitialLifeSpan() * (SOUL_BAR_WIDTH + 1)), 0, SOUL_BAR_WIDTH);
			guiGraphics.blit(SOUL_BAR_BACKGROUND, xPos, yPos, 0, 0, SOUL_BAR_WIDTH, SOUL_BAR_HEIGHT, SOUL_BAR_WIDTH, SOUL_BAR_HEIGHT);
			if (fill > 0) {
				guiGraphics.blit(SOUL_BAR_PROGRESS, xPos, yPos, 0, 0, fill, SOUL_BAR_HEIGHT, SOUL_BAR_WIDTH, SOUL_BAR_HEIGHT);
			}
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void cancelRespawnScreen(ScreenEvent.Opening event) {
		if (event.getNewScreen() instanceof DeathScreen) {
			Minecraft mc = Minecraft.getInstance();
			StandPower standPower = ClientPowerCache.getPower(PowerClass.STAND);
			if (standPower == null && mc.player != null) {
				standPower = StandPower.get(mc.player);
			}
			if (!soulEntityWaiting && firstDeathFrame && standPower != null && standPower.willSoulSpawn()) {
				soulEntityWaiting = true;
				firstDeathFrame = false;
			}
			if (isCameraEntityPlayerSoul() || soulEntityWaiting) {
				event.setNewScreen(null);
			}
		}
	}

	public static void onSoulFailedSpawn() {
		soulEntityWaiting = false;
	}
}
