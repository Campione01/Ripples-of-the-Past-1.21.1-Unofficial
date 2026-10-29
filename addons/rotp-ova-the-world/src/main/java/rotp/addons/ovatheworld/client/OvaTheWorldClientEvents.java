package rotp.addons.ovatheworld.client;

import rotp.core.client.entityrender.stand.StandEntityRenderer;

import rotp.addons.ovatheworld.OvaTheWorldAddon;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(
        modid = OvaTheWorldAddon.MOD_ID,
        value = Dist.CLIENT)
public final class OvaTheWorldClientEvents {
    private OvaTheWorldClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                OvaTheWorldAddon.THE_WORLD_OVA_ENTITY.get(),
                StandEntityRenderer::new);
    }
}
