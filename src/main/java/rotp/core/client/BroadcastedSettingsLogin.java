package rotp.core.client;

import rotp.core.config.client.ClientModSettings;
import rotp.core.core.JojoMod;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * 1.16 ClientEventHandler.clientLoggedIn: the client sends its broadcast settings (aim assist, keep attack target, ...)
 * on every login, so a world or server that has none stored, or older ones, follows the client's own.
 */
@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT)
public final class BroadcastedSettingsLogin {
	private BroadcastedSettingsLogin() {}

	@SubscribeEvent
	public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
		if (ClientModSettings.getInstance() != null) {
			ClientModSettings.getSettingsReadOnly().broadcasted.broadcastToServer();
		}
	}
}
