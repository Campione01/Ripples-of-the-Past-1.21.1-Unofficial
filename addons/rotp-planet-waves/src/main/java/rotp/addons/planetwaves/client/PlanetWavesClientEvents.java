package rotp.addons.planetwaves.client;

import rotp.core.client.entityrender.stand.StandEntityRenderer;

import rotp.addons.planetwaves.PlanetWavesAddon;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

@EventBusSubscriber(
        modid = PlanetWavesAddon.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class PlanetWavesClientEvents {
    private PlanetWavesClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                PlanetWavesAddon.PLANET_WAVES_ENTITY.get(),
                StandEntityRenderer::new);
        event.registerEntityRenderer(
                PlanetWavesAddon.METEORITE.get(),
                MeteoriteRenderer::new);
    }

    @SubscribeEvent
    public static void registerGuiLayers(
            RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                PlanetWavesAddon.id("true_blindness_overlay"),
                new TrueBlindnessOverlay());
    }
}
