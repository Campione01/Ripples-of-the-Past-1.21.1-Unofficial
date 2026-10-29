package rotp.addons.novemberrain.client;

import rotp.core.client.entityrender.stand.StandEntityRenderer;

import rotp.addons.novemberrain.NovemberRainAddon;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = NovemberRainAddon.MOD_ID, value = Dist.CLIENT)
public final class NovemberRainClientEvents {
    private NovemberRainClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                NovemberRainAddon.NOVEMBER_RAIN_ENTITY.get(),
                StandEntityRenderer::new);
    }
}
