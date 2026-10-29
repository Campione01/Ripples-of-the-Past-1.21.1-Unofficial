package rotp.addons.jumpingjackflash.client;

import rotp.core.client.entityrender.stand.StandEntityRenderer;

import rotp.addons.jumpingjackflash.JumpingJackFlashAddon;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(
        modid = JumpingJackFlashAddon.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class JumpingJackFlashClientEvents {
    private JumpingJackFlashClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                JumpingJackFlashAddon.STAND_ENTITY.get(),
                StandEntityRenderer::new);
        event.registerEntityRenderer(
                JumpingJackFlashAddon.TRACKING_BULLET.get(),
                context -> new ThrownItemRenderer<>(
                        context, 0.35F, true));
        event.registerEntityRenderer(
                JumpingJackFlashAddon.TRACKING_SNOWBALL.get(),
                context -> new ThrownItemRenderer<>(
                        context, 1.0F, false));
        event.registerEntityRenderer(
                JumpingJackFlashAddon.GRAVITY_DOMAIN.get(),
                GravityDomainRenderer::new);
    }
}
