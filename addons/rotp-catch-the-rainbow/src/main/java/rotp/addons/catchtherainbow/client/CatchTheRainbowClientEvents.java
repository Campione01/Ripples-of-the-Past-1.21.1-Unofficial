package rotp.addons.catchtherainbow.client;

import rotp.addons.catchtherainbow.CatchTheRainbowAddon;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;

@EventBusSubscriber(
        modid = CatchTheRainbowAddon.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class CatchTheRainbowClientEvents {
    private CatchTheRainbowClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                CatchTheRainbowAddon.CATCH_THE_RAINBOW_ENTITY.get(),
                CatchTheRainbowRenderer::new);
        event.registerEntityRenderer(
                CatchTheRainbowAddon.RAIN_BLADE_ENTITY_1.get(),
                RainBladeRenderer::new);
        event.registerEntityRenderer(
                CatchTheRainbowAddon.RAIN_BLADE_ENTITY_2.get(),
                RainBladeRenderer::new);
        event.registerEntityRenderer(
                CatchTheRainbowAddon.RAIN_BLADE_ENTITY_3.get(),
                RainBladeRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(
            EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(
                CatchTheRainbowModel.LAYER_LOCATION,
                CatchTheRainbowModel::createBodyLayer);
        event.registerLayerDefinition(
                CatchTheRainbowMaskModel.LAYER_LOCATION,
                CatchTheRainbowMaskModel::createBodyLayer);
        event.registerLayerDefinition(
                RainBladeModel.LAYER_LOCATION,
                RainBladeModel::createBodyLayer);
        event.registerLayerDefinition(
                CatchTheRainbowRainModel.LAYER_LOCATION,
                CatchTheRainbowRainModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void addLayers(
            EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer == null) {
                continue;
            }
            renderer.addLayer(
                    new CatchTheRainbowWearableLayer(
                            renderer,
                            new CatchTheRainbowMaskModel(
                                    event.getEntityModels()
                                            .bakeLayer(
                                                    CatchTheRainbowMaskModel
                                                            .LAYER_LOCATION))));
            renderer.addLayer(
                    new CatchTheRainbowRainLayer(
                            renderer,
                            new CatchTheRainbowRainModel(
                                    event.getEntityModels()
                                            .bakeLayer(
                                                    CatchTheRainbowRainModel
                                                            .LAYER_LOCATION))));
        }
    }

    @SubscribeEvent
    public static void registerClientExtensions(
            RegisterClientExtensionsEvent event) {
        event.registerItem(
                new CatchTheRainbowMaskExtensions(),
                CatchTheRainbowAddon.MASK_ITEM);
    }
}
