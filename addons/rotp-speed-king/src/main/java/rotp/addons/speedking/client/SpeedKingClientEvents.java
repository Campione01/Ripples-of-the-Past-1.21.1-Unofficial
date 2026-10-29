package rotp.addons.speedking.client;

import rotp.core.client.ui.marker.MarkerRenderer;

import rotp.addons.speedking.SpeedKingAddon;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(
        modid = SpeedKingAddon.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class SpeedKingClientEvents {
    private SpeedKingClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                SpeedKingAddon.SPEED_KING_ENTITY.get(),
                SpeedKingRenderer::new);
        event.registerEntityRenderer(
                SpeedKingAddon.PICKAXE_ENTITY.get(),
                SpeedKingPickaxeRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(
            EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(
                SpeedKingPickaxeModel.LAYER,
                SpeedKingPickaxeModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() ->
                MarkerRenderer.registerMarkerRenderer(
                        new HeatTrapMarker(Minecraft.getInstance())));
    }
}
