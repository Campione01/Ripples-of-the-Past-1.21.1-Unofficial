package rotp.addons.lovers.client;

import rotp.addons.lovers.LoversAddon;
import rotp.addons.lovers.client.render.LoversRenderer;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(
        modid = LoversAddon.MOD_ID,
        value = Dist.CLIENT)
public final class LoversClientEvents {
    private LoversClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                LoversAddon.LOVERS_ENTITY.get(),
                LoversRenderer::new);
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() ->
                rotp.core.client.ui.marker
                        .MarkerRenderer.registerMarkerRenderer(
                                new LoversTargetMarker(
                                        Minecraft.getInstance())));
    }
}
