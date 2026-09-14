package rotp.core.init;

import rotp.core.core.JojoMod;
import rotp.core.client.ui.screen.walkman.WalkmanScreen;
import rotp.core.mechanics.clothes.client.ui.PlayerClothesScreen;
import rotp.core.mechanics.clothes.sewing.client.SewingMachineScreen;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT)
public class ModContainerScreensClient {

	@SubscribeEvent
	public static void registerScreens(RegisterMenuScreensEvent event) {
		event.register(ModContainers.PLAYER_CLOTHES.get(), PlayerClothesScreen::new);
		event.register(ModContainers.SEWING_MACHINE.get(), SewingMachineScreen::new);
		event.register(ModContainers.WALKMAN.get(), WalkmanScreen::new);
	}
	
}
