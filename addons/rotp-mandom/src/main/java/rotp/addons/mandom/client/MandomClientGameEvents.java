package rotp.addons.mandom.client;

import rotp.addons.mandom.MandomAddon;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(
        modid = MandomAddon.MOD_ID,
        value = Dist.CLIENT)
public final class MandomClientGameEvents {
    private MandomClientGameEvents() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        MandomRewindPostEffect.tick();
    }

    @SubscribeEvent
    public static void onLogout(
            ClientPlayerNetworkEvent.LoggingOut event) {
        MandomRewindPostEffect.reset();
    }
}
