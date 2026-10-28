package rotp.core.subsystems.soul.client;

import java.lang.reflect.Field;

import javax.annotation.Nullable;

import rotp.core.client.ClientPowerCache;
import rotp.core.client.ClientProxy;
import rotp.core.client.util.functions.ClientUtil;
import rotp.core.core.JojoMod;
import rotp.core.network.c2s.ClRemovePlayerSoulEntityPacket;
import rotp.core.network.c2s.ClSoulRotationPacket;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.soul.SoulAscensionClientState;
import rotp.core.subsystems.soul.SoulEntity;
import rotp.core.util.reflection.ReflectionUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
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
import net.neoforged.fml.util.ObfuscationReflectionHelper;
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
	private static final Component SKIP_PROMPT = Component.translatable(
			"jojo_ripples.message.skip_soul_ascension", Component.keybind("key.jump"));
	// 1.16 took the prompt off the action bar through reflection, and only while it was still the message there
	private static final Field GUI_OVERLAY_MESSAGE = ObfuscationReflectionHelper.findField(Gui.class, "overlayMessageString");
	private static final Field GUI_OVERLAY_MESSAGE_TIME = ObfuscationReflectionHelper.findField(Gui.class, "overlayMessageTime");
	private static SoulEntity playerSoulEntity;
	private static final SoulAscensionClientState STATE = new SoulAscensionClientState();
	private static boolean skipPromptShown;

	private ControllerSoul() {}

	@SubscribeEvent
	public static void tick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			playerSoulEntity = null;
			STATE.reset();
			skipPromptShown = false;
			return;
		}

		boolean playerDead = mc.player.isDeadOrDying();
		updatePlayerSoul(mc, playerDead);
		SoulEntity soulOnCamera = ownSoulOnCamera(mc);
		if (soulOnCamera != null) {
			// the soul took its player's look in its tick
			PacketDistributor.sendToServer(new ClSoulRotationPacket(
					soulOnCamera.getId(), soulOnCamera.getYRot(), soulOnCamera.getXRot()));
		}
		if (STATE.skipsAscension(soulOnCamera != null, mc.options.keyJump.isDown(),
				mc.screen != null || mc.getOverlay() != null)) {
			skipAscension(soulOnCamera);
		}
		mc.player.deathTime = STATE.keptDeathTime(playerDead, mc.player.deathTime);
		updateSkipPrompt(mc, STATE.showsSkipPrompt(playerDead));
	}

	private static void updatePlayerSoul(Minecraft mc, boolean playerDead) {
		SoulEntity soulOnCamera = ownSoulOnCamera(mc);
		if (soulOnCamera != null) {
			playerSoulEntity = soulOnCamera;
		}

		STATE.tick(playerSoulEntity != null && playerSoulEntity.isAlive() && playerSoulEntity.getOriginEntity() == mc.player,
				playerDead, mc.player.isSpectator());
		if (playerSoulEntity == null) {
			return;
		}
		Entity cameraEntity = mc.getCameraEntity();
		if (!STATE.followsSoul()) {
			if (cameraEntity == playerSoulEntity) {
				ClientUtil.setCameraEntityPreventShaderSwitch(mc.player);
			}
			playerSoulEntity = null;
		}
		else if (cameraEntity != playerSoulEntity) {
			ClientUtil.setCameraEntityPreventShaderSwitch(playerSoulEntity);
		}
	}

	/** The camera is on the player's live soul, also before a client tick has taken the soul up. */
	@Nullable
	private static SoulEntity ownSoulOnCamera(Minecraft mc) {
		return mc.player != null && !mc.player.isSpectator()
				&& mc.getCameraEntity() instanceof SoulEntity soulEntity
				&& soulEntity.isAlive() && soulEntity.getOriginEntity() == mc.player ? soulEntity : null;
	}

	public static boolean isCameraEntityPlayerSoul() {
		return ownSoulOnCamera(Minecraft.getInstance()) != null;
	}

	private static void skipAscension(@Nullable SoulEntity soulOnCamera) {
		if (soulOnCamera != null && soulOnCamera.getLifeSpan() > 0) {
			PacketDistributor.sendToServer(new ClRemovePlayerSoulEntityPacket(soulOnCamera.getId()));
			soulOnCamera.skipAscension();
		}
		STATE.skip();
	}

	private static void updateSkipPrompt(Minecraft mc, boolean show) {
		if (show) {
			ClientProxy.setOverlayMessage(SKIP_PROMPT, false);
		}
		else if (skipPromptShown && SKIP_PROMPT.equals(ReflectionUtil.getFieldValue(GUI_OVERLAY_MESSAGE, mc.gui))) {
			ReflectionUtil.setFieldValue(GUI_OVERLAY_MESSAGE, mc.gui, null);
			ReflectionUtil.setIntFieldValue(GUI_OVERLAY_MESSAGE_TIME, mc.gui, 0);
		}
		skipPromptShown = show;
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
		Minecraft mc = Minecraft.getInstance();
		SoulEntity soulOnCamera = ownSoulOnCamera(mc);
		// the layer events come with the HUD hidden too; 1.16's experience bar event did not
		if (soulOnCamera == null || mc.options.hideGui) {
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
			int fill = Mth.clamp((int) ((float) soulOnCamera.getLifeSpan() / (float) soulOnCamera.getInitialLifeSpan() * (SOUL_BAR_WIDTH + 1)), 0, SOUL_BAR_WIDTH);
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
			if (STATE.holdsDeathScreen(isCameraEntityPlayerSoul(), standPower != null && standPower.willSoulSpawn())) {
				event.setNewScreen(null);
			}
		}
	}

	public static void onSoulFailedSpawn() {
		STATE.stopWaiting();
	}
}
