package rotp.addons.thehand.client;

import rotp.addons.thehand.TheHandAddon;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = TheHandAddon.MOD_ID, value = Dist.CLIENT)
public final class TheHandClientEvents {
    private TheHandClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                TheHandAddon.THE_HAND_ENTITY.get(),
                TheHandRenderer::new);
    }
}
