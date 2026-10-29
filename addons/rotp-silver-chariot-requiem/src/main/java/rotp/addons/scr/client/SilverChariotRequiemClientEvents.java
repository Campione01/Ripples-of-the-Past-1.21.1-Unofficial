package rotp.addons.scr.client;

import rotp.core.client.particle.type.MeteoriteVirusParticle;

import rotp.addons.scr.SilverChariotRequiemAddon;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

@EventBusSubscriber(
        modid = SilverChariotRequiemAddon.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class SilverChariotRequiemClientEvents {
    private SilverChariotRequiemClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                SilverChariotRequiemAddon
                        .SILVER_CHARIOT_REQUIEM_ENTITY.get(),
                SilverChariotRequiemRenderer::new);
    }

    @SubscribeEvent
    public static void registerParticles(
            RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(
                SilverChariotRequiemAddon
                        .CHARIOT_VIRUS_PARTICLE.get(),
                MeteoriteVirusParticle.Factory::new);
    }
}
