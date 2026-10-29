package rotp.addons.stonefree.client;

import rotp.addons.stonefree.StoneFreeAddon;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = StoneFreeAddon.MOD_ID, value = Dist.CLIENT)
public final class StoneFreeClientEvents {
    private StoneFreeClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                StoneFreeAddon.STONE_FREE_ENTITY.get(),
                StoneFreeStandRenderer::new);
        event.registerEntityRenderer(
                StoneFreeAddon.SF_STRING.get(),
                StoneFreeStringRenderer::new);
        event.registerEntityRenderer(
                StoneFreeAddon.SFU_STRING.get(),
                StoneFreeStringRenderer::new);
        event.registerEntityRenderer(
                StoneFreeAddon.SFU_STRING_SWEEP.get(),
                StoneFreeStringRenderer::new);
        event.registerEntityRenderer(
                StoneFreeAddon.SFU_STRING_WHIP.get(),
                StoneFreeStringRenderer::new);
        event.registerEntityRenderer(
                StoneFreeAddon.SF_EXTENDED_PUNCH.get(),
                StoneFreeStringRenderer::new);
        event.registerEntityRenderer(
                StoneFreeAddon.SFU_STRING_SPIKE.get(),
                StoneFreeStringRenderer::new);
        event.registerEntityRenderer(
                StoneFreeAddon.SF_GRAPPLING_STRING.get(),
                StoneFreeStringRenderer::new);
        event.registerEntityRenderer(
                StoneFreeAddon.SFU_GRAPPLING_STRING.get(),
                StoneFreeStringRenderer::new);
        event.registerEntityRenderer(
                StoneFreeAddon.SFU_BARRIER.get(),
                StoneFreeStringRenderer::new);
    }

    // 1.16 added the string decomposition and Mobius Strip layers to every biped renderer as well as to both player
    // skins, so mobs carrying Stone Free show them too.
    @SubscribeEvent
    public static void addLayers(
            EntityRenderersEvent.AddLayers event) {
        for (var entityType : event.getEntityTypes()) {
            addLayer(event.getRenderer(entityType));
        }
        for (var skin : event.getSkins()) {
            addLayer(event.getSkin(skin));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addLayer(EntityRenderer<?> renderer) {
        if (renderer
                instanceof LivingEntityRenderer livingRenderer) {
            livingRenderer.addLayer(
                    new StoneFreeOverlayLayer(livingRenderer));
        }
    }
}
