package rotp.addons.guanggun.client;

import rotp.core.client.entityrender.stand.StandEntityRenderer;

import rotp.addons.guanggun.GuanggunAddon;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(
        modid = GuanggunAddon.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class GuanggunClientEvents {
    private GuanggunClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                GuanggunAddon.GUANGGUN_ENTITY.get(),
                StandEntityRenderer::new);
        event.registerEntityRenderer(
                GuanggunAddon.GUANGGUN_BULLET.get(),
                GuanggunBulletRenderer::new);
    }
}
