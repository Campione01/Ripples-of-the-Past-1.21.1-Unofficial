package rotp.addons.aquanecklace.client;

import rotp.core.client.ui.marker.MarkerRenderer;

import rotp.addons.aquanecklace.AquaNecklaceAddon;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(
        modid = AquaNecklaceAddon.MOD_ID,
        value = Dist.CLIENT)
public final class AquaNecklaceClientEvents {
    private AquaNecklaceClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                AquaNecklaceAddon
                        .AQUA_NECKLACE_ENTITY.get(),
                AquaNecklaceRenderer::new);
    }

    @SubscribeEvent
    public static void clientSetup(
            FMLClientSetupEvent event) {
        event.enqueueWork(() ->
                MarkerRenderer.registerMarkerRenderer(
                        new AquaNecklaceHostMarker(
                                Minecraft.getInstance())));
    }
}
