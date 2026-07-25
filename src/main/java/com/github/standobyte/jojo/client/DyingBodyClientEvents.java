package com.github.standobyte.jojo.client;

import com.github.standobyte.jojo.JojoModLivingVariables;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanData;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanPowerType;

import net.minecraft.client.Minecraft;
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
}
