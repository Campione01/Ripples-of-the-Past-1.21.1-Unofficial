package rotp.addons.spicegirl.client;

import rotp.addons.spicegirl.SpiceGirlAddon;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(
        modid = SpiceGirlAddon.MOD_ID,
        bus = EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class SpiceGirlClientEvents {
    private SpiceGirlClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                SpiceGirlAddon.SPICE_GIRL_ENTITY.get(),
                SpiceGirlStandRenderer::new);
    }
}
