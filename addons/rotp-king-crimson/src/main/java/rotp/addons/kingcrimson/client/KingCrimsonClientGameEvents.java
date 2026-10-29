package rotp.addons.kingcrimson.client;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(
        modid = KingCrimsonAddon.MOD_ID,
        value = Dist.CLIENT)
public final class KingCrimsonClientGameEvents {
    private KingCrimsonClientGameEvents() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        EpitaphPostEffect.syncFromLocalPlayer();
        EpitaphForesightClient.tick();
        KingCrimsonTemporalClientState.sync();
        KingCrimsonTemporalPostEffect.sync();
    }
}
