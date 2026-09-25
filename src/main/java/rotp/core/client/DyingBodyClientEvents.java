package rotp.core.client;

import rotp.core.JojoModLivingVariables;
import rotp.core.core.JojoMod;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.impl.powers.pillarman.PillarmanData;
import rotp.core.impl.powers.pillarman.PillarmanPowerType;
import rotp.core.client.util.functions.ClientUtil;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT)
public class DyingBodyClientEvents {
	private static final ResourceLocation VIGNETTE_LOCATION = JojoMod.resLoc("textures/vignette.png");

	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void hideDyingBodyVanillaHud(RenderGuiLayerEvent.Pre event) {
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		if (player == null || player.isSpectator()) {
			return;
		}

		ResourceLocation layerName = event.getName();
		boolean isFoodOrAirLayer = layerName.equals(VanillaGuiLayers.FOOD_LEVEL)
				|| layerName.equals(VanillaGuiLayers.AIR_LEVEL);
		if (isFoodOrAirLayer && PlayerPower.getPowerData(player, PillarmanPowerType.PILLAR_MAN)
				.map(PillarmanData::getEvolutionStage)
				.filter(stage -> stage >= 2)
				.isPresent()) {
			event.setCanceled(true);
			return;
		}

		if (JojoModLivingVariables.get(player).isDyingBody()
				&& (layerName.equals(VanillaGuiLayers.PLAYER_HEALTH) || isFoodOrAirLayer)) {
			event.setCanceled(true);
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void darkenDyingBodyFog(ViewportEvent.ComputeFogColor event) {
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		if (player == null || player.isSpectator() || player.isDeadOrDying()) {
			return;
		}

		JojoModLivingVariables playerVars = JojoModLivingVariables.get(player);
		if (playerVars.isDyingBody() && playerVars.getDyingBodyTicksLeft() <= 21) {
			event.setRed(0.0F);
			event.setGreen(0.0F);
			event.setBlue(0.0F);
		}
	}

	// 1.16 renderLosingVision: drawn with the camera overlays (1.16 HELMET slot), not gated by fancy graphics
	@SubscribeEvent
	public static void renderLosingVision(RenderGuiLayerEvent.Pre event) {
		if (!event.getName().equals(VanillaGuiLayers.CAMERA_OVERLAYS)) {
			return;
		}
		float vignette = DyingBodyVision.losingVisionVignette(Minecraft.getInstance().player,
				ClientUtil.partialTick(event.getPartialTick(), false));
		if (vignette > 0.0F) {
			renderVignette(event.getGuiGraphics(), vignette);
		}
	}

	// 1.16 dyingBodyLostVision: the fog closes in over the last 20 ticks
	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void closeInDyingBodyFog(ViewportEvent.RenderFog event) {
		float closeIn = DyingBodyVision.closingFogProgress(Minecraft.getInstance().player, (float) event.getPartialTick());
		if (closeIn > 0.0F) {
			event.setNearPlaneDistance(DyingBodyVision.closeIn(event.getNearPlaneDistance(), closeIn));
			event.setFarPlaneDistance(DyingBodyVision.closeIn(event.getFarPlaneDistance(), closeIn));
			event.setCanceled(true);
		}
	}

	private static void renderVignette(GuiGraphics guiGraphics, float vignette) {
		RenderSystem.disableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.enableBlend();
		RenderSystem.blendFuncSeparate(
				GlStateManager.SourceFactor.ZERO,
				GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
				GlStateManager.SourceFactor.ONE,
				GlStateManager.DestFactor.ZERO);
		guiGraphics.setColor(vignette, vignette, vignette, 1.0F);
		guiGraphics.blit(VIGNETTE_LOCATION, 0, 0, -90, 0.0F, 0.0F,
				guiGraphics.guiWidth(), guiGraphics.guiHeight(), guiGraphics.guiWidth(), guiGraphics.guiHeight());
		RenderSystem.depthMask(true);
		RenderSystem.enableDepthTest();
		guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableBlend();
	}
}
