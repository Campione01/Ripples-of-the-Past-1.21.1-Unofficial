package rotp.addons.cream.client;

import rotp.addons.cream.CreamAddon;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

@EventBusSubscriber(
        modid = CreamAddon.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class CreamClientEvents {
    private CreamClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                CreamAddon.CREAM_ENTITY.get(),
                CreamEntityRenderer::new);
    }

    @SubscribeEvent
    public static void registerGuiLayers(
            RegisterGuiLayersEvent event) {
        // 1.16 drew both screens in RenderGameOverlayEvent.Pre(ALL): under the hotbar, hearts and chat, not over them
        event.registerBelowAll(
                CreamAddon.id("void_overlay"),
                new CreamVoidOverlay());
    }
}
