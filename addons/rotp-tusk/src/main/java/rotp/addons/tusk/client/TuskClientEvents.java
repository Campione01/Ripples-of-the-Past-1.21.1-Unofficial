package rotp.addons.tusk.client;

import rotp.core.client.entityrender.stand.StandEntityRenderer;

import rotp.addons.tusk.TuskAddon;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

@EventBusSubscriber(
        modid = TuskAddon.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class TuskClientEvents {
    private TuskClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                TuskAddon.TUSK_ENTITY.get(), StandEntityRenderer::new);
        event.registerEntityRenderer(
                TuskAddon.NAIL_ENTITY.get(), NailRenderer::new);
        event.registerEntityRenderer(
                TuskAddon.WORMHOLE_ENTITY.get(),
                WormholeRenderer::new);
        event.registerEntityRenderer(
                TuskAddon.WORMHOLE_PORTAL_ENTITY.get(),
                WormholePortalRenderer::new);
        event.registerEntityRenderer(
                TuskAddon.WORMHOLE_ARM_ENTITY.get(),
                WormholeArmRenderer::new);
    }

    @SubscribeEvent
    public static void addPlayerLayers(
            EntityRenderersEvent.AddLayers event) {
        for (var skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer != null) {
                renderer.addLayer(new TuskArmPortalLayer(renderer));
            }
        }
    }

    @SubscribeEvent
    public static void registerParticleProviders(
            RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(
                TuskAddon.NAIL_SWIPE.get(),
                NailSwipeParticle.Factory::new);
    }
}
