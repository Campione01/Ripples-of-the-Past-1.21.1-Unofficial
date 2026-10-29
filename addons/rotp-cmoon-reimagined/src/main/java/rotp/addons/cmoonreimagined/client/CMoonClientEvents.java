package rotp.addons.cmoonreimagined.client;

import rotp.core.client.entityrender.stand.StandEntityRenderer;

import rotp.addons.cmoonreimagined.CMoonAddon;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(
        modid = CMoonAddon.MOD_ID,
        value = Dist.CLIENT)
public final class CMoonClientEvents {
    private CMoonClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                CMoonAddon.C_MOON_ENTITY.get(),
                StandEntityRenderer::new);
    }
}
