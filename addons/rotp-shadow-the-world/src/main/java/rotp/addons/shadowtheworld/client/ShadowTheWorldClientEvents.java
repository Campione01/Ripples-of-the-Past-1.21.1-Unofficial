package rotp.addons.shadowtheworld.client;

import rotp.core.client.entityrender.stand.StandEntityRenderer;

import rotp.addons.shadowtheworld.ShadowTheWorldAddon;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(
        modid = ShadowTheWorldAddon.MOD_ID,
        value = Dist.CLIENT)
public final class ShadowTheWorldClientEvents {
    private ShadowTheWorldClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                ShadowTheWorldAddon
                        .SHADOW_THE_WORLD_ENTITY
                        .get(),
                StandEntityRenderer::new);
    }

    /** 1.16 attached the post-evolution glow layer to the Stand renderer. */
    @SubscribeEvent
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void addLayers(
            EntityRenderersEvent.AddLayers event) {
        EntityRenderer<?> renderer = event.getRenderer(
                ShadowTheWorldAddon.SHADOW_THE_WORLD_ENTITY.get());
        if (renderer instanceof LivingEntityRenderer livingRenderer) {
            livingRenderer.addLayer(
                    new ShadowTheWorldGlowLayer(livingRenderer));
        }
    }
}
