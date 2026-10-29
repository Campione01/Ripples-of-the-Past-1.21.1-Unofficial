package rotp.addons.purplehaze.client;

import rotp.addons.purplehaze.PurpleHazeAddon;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

@EventBusSubscriber(modid = PurpleHazeAddon.MOD_ID, value = Dist.CLIENT)
public final class PurpleHazeClientEvents {
    private PurpleHazeClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                PurpleHazeAddon.PURPLE_HAZE_ENTITY.get(),
                PurpleHazeStandRenderer::new);
        event.registerEntityRenderer(
                PurpleHazeAddon.CAPSULE_PROJECTILE.get(),
                CapsuleProjectileRenderer::new);
        event.registerEntityRenderer(
                PurpleHazeAddon.PURPLE_HAZE_CLOUD.get(),
                PurpleHazeCloudRenderer::new);
    }

    @SubscribeEvent
    public static void registerParticles(
            RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(
                PurpleHazeAddon
                        .PURPLE_HAZE_VIRUS_PARTICLE
                        .get(),
                PurpleHazeVirusParticle.Provider::new);
    }
}
