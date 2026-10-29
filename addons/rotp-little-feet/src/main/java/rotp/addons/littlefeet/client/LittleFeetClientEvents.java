package rotp.addons.littlefeet.client;

import rotp.core.client.entityrender.stand.StandEntityRenderer;

import rotp.addons.littlefeet.LittleFeetAddon;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(
        modid = LittleFeetAddon.MOD_ID,
        bus = EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class LittleFeetClientEvents {
    private LittleFeetClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                LittleFeetAddon.LITTLE_FEET_ENTITY.get(),
                StandEntityRenderer::new);
    }
}
