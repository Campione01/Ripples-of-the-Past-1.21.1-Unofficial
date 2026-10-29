package rotp.addons.kiss.client;

import rotp.core.client.entityrender.stand.StandEntityRenderer;

import rotp.addons.kiss.KissAddon;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(
        modid = KissAddon.MOD_ID,
        value = Dist.CLIENT)
public final class KissClientEvents {
    private KissClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                KissAddon.KISS_ENTITY.get(), StandEntityRenderer::new);
        event.registerEntityRenderer(
                KissAddon.STICKER_ENTITY.get(), KissStickerRenderer::new);
        event.registerEntityRenderer(
                KissAddon.BOMB_ENTITY.get(), KissBombRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(
            EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(
                KissPlateModel.LAYER, KissPlateModel::createLayer);
    }
}
