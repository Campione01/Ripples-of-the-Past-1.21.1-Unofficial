package rotp.addons.metallica.client;

import rotp.core.client.entityrender.entities.NoopEntityRenderer;

import rotp.addons.metallica.MetallicaAddon;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(
        modid = MetallicaAddon.MOD_ID,
        value = Dist.CLIENT)
public final class MetallicaClientEvents {
    private MetallicaClientEvents() {}

    @SubscribeEvent
    public static void clientSetup(
            FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(
                MetallicaAddon.SCALPEL.get(),
                MetallicaAddon.id("count"),
                (stack, level, entity, seed) ->
                        stack.getCount()));
    }

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                MetallicaAddon.METALLICA_ENTITY.get(),
                NoopEntityRenderer::new);
    }

    @SubscribeEvent
    public static void addLayers(
            EntityRenderersEvent.AddLayers event) {
        for (var entityType : event.getEntityTypes()) {
            addLayer(event.getRenderer(entityType));
        }
        for (var skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            addLayer(renderer);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addLayer(
            EntityRenderer<?> renderer) {
        if (renderer
                instanceof LivingEntityRenderer livingRenderer) {
            livingRenderer.addLayer(
                    new LackOfIronLayer(livingRenderer));
        }
    }
}
