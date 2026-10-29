package rotp.addons.achtungbaby.client;

import rotp.core.client.entityrender.stand.StandEntityRenderer;

import rotp.addons.achtungbaby.AchtungBabyAddon;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(
        modid = AchtungBabyAddon.MOD_ID,
        bus = EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class AchtungBabyClientEvents {
    private AchtungBabyClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                AchtungBabyAddon.ACHTUNG_BABY_ENTITY.get(),
                StandEntityRenderer::new);
        // 1.16's rune was spawned with the vanilla snowball type, so every client drew it as a snowball; the donor's
        // renderer mixin never matched that client-side snowball.
        event.registerEntityRenderer(
                AchtungBabyAddon.INVISIBILITY_RUNE.get(),
                ThrownItemRenderer::new);
    }
}
