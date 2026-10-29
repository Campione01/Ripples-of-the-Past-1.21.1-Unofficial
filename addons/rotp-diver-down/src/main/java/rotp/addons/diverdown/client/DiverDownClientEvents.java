package rotp.addons.diverdown.client;

import rotp.core.client.entityrender.stand.StandEntityRenderer;
import rotp.core.client.ui.marker.MarkerRenderer;

import rotp.addons.diverdown.DiverDownAddon;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(
        modid = DiverDownAddon.MOD_ID,
        value = Dist.CLIENT)
public final class DiverDownClientEvents {
    private DiverDownClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                DiverDownAddon.DIVER_DOWN_ENTITY.get(),
                StandEntityRenderer::new);
        event.registerEntityRenderer(
                DiverDownAddon.KINETIC_TRAP_ENTITY.get(),
                DiverDownTrapRenderer::new);
        event.registerEntityRenderer(
                DiverDownAddon.BONE_SHARD_ENTITY.get(),
                BoneShardRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(
            EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(
                DiverDownTrapModel.LAYER_LOCATION,
                DiverDownTrapModel::createBodyLayer);
        event.registerLayerDefinition(
                BoneShardModel.LAYER_LOCATION,
                BoneShardModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            MarkerRenderer.registerMarkerRenderer(
                    new DiverDownInsideMarker(minecraft));
            MarkerRenderer.registerMarkerRenderer(
                    new DiverDownTrapMarker(minecraft));
        });
    }
}
