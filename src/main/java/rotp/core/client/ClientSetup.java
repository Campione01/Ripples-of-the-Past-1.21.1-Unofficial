package rotp.core.client;

import java.io.File;

import rotp.core.client.input.InputHandler;
import rotp.core.client.itemrender.ModItemModelOverrides;
import rotp.core.client.entityrender.stand.aura.StandAuraFxClient;
import rotp.core.client.ui.screen_jojomenu.JojoMenuTabs;
import rotp.core.config.client.ClientModSettings;
import rotp.core.core.JojoMod;
import rotp.core.init.ModBlocks;
import rotp.core.subsystems.entity_puppetcontrol.client.stand.StandHudElements;
import rotp.core.impl.powers.hamon.client.particle.custom.FirstPersonHamonAura;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT)
public class ClientSetup {
	
	@SubscribeEvent
	public static void veryEarlyClientSetup(RegisterClientReloadListenersEvent event) {
		initClientSettings();
//		HudControlSettings.init(new File(mc.gameDirectory, "config/jojo_rotp/controls/"));
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onClientSetup0(FMLClientSetupEvent event) {
		AbilitySelectionVisualCorePolicies.register();
		StandAuraFxClient.register();
		JojoMenuTabs.initDefaults();
	}

	@SubscribeEvent
	public static void onClientSetup(FMLClientSetupEvent event) {
		initClientSettings();
		Minecraft mc = Minecraft.getInstance();
		ModMarkers.registerMarkers(mc);
		StandHudElements.init();
		FirstPersonHamonAura.init();
			event.enqueueWork(() -> {
			ModItemModelOverrides.register();
			ModBlocks.WOODEN_COFFIN_OAK.values().forEach(block -> ItemBlockRenderTypes.setRenderLayer(block.get(), RenderType.cutout()));
			ItemBlockRenderTypes.setRenderLayer(ModBlocks.SLUMBERING_PILLARMAN.get(), RenderType.cutout());
		});
	}
	
	@SubscribeEvent
	public static void registerKeyBindings(RegisterKeyMappingsEvent event) {
		initClientSettings();
		InputHandler.init(event);
	}

	private static void initClientSettings() {
		Minecraft mc = Minecraft.getInstance();
		ClientModSettings.init(new File(mc.gameDirectory, "config/jojo_rotp/client_settings.json"));
	}
}
