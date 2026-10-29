package rotp.addons.tickettoride.client;

import rotp.addons.tickettoride.TicketToRideAddon;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

@EventBusSubscriber(
        modid = TicketToRideAddon.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class TicketToRideClientEvents {
    private TicketToRideClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                TicketToRideAddon.TICKET_TO_RIDE_ENTITY.get(),
                TicketToRideControllerRenderer::new);
        event.registerEntityRenderer(
                TicketToRideAddon.DEAD_PHOTO_MEMENTO.get(),
                DeadPhotoMementoRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(
            EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(
                DeadPhotoMementoModel.LAYER_LOCATION,
                DeadPhotoMementoModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerParticles(
            RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(
                TicketToRideAddon.LOVE_TRAIN_LIGHT_WALL.get(),
                TicketToRideParticle.Provider::new);
        event.registerSpriteSet(
                TicketToRideAddon.LOVE_TRAIN_LIGHT.get(),
                TicketToRideParticle.Provider::new);
        event.registerSpriteSet(
                TicketToRideAddon.LOVE_TRAIN_LIGHT_PILLAR.get(),
                TicketToRideParticle.Provider::new);
        event.registerSpriteSet(
                TicketToRideAddon.LOVE_TRAIN_LIGHT_SHORT.get(),
                TicketToRideParticle.Provider::new);
        event.registerSpriteSet(
                TicketToRideAddon.LOVE_TRAIN_LIGHT_BALL.get(),
                TicketToRideParticle.Provider::new);
    }
}
