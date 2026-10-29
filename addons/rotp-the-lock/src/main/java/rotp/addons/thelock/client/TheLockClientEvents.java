package rotp.addons.thelock.client;

import rotp.core.client.ClientGlobals;
import rotp.core.client.entityrender.entities.NoopEntityRenderer;
import rotp.core.client.ui.marker.MarkerRenderer;

import rotp.addons.thelock.TheLockAddon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(
        modid = TheLockAddon.MOD_ID,
        value = Dist.CLIENT)
public final class TheLockClientEvents {
    private TheLockClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                TheLockAddon.THE_LOCK_ENTITY.get(),
                NoopEntityRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(
            EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(
                TheLockLayerModel.LAYER_LOCATION,
                TheLockLayerModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void addLayers(
            EntityRenderersEvent.AddLayers event) {
        for (var entityType : event.getEntityTypes()) {
            addLayer(
                    event.getRenderer(entityType),
                    event.getEntityModels().bakeLayer(
                            TheLockLayerModel.LAYER_LOCATION));
        }
        for (var skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            addLayer(
                    renderer,
                    event.getEntityModels().bakeLayer(
                            TheLockLayerModel.LAYER_LOCATION));
        }
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(
                    TheLockAddon.THE_LOCK_ITEM.get(),
                    TheLockAddon.id("stand_invisible"),
                    (stack, level, entity, seed) ->
                            ClientGlobals.canSeeStands
                                    ? 0.0F
                                    : 1.0F);
            MarkerRenderer.registerMarkerRenderer(
                    new TheLockMarkerRenderer(
                            Minecraft.getInstance()));
        });
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addLayer(
            EntityRenderer<?> renderer, ModelPart modelRoot) {
        if (renderer instanceof LivingEntityRenderer livingRenderer) {
            livingRenderer.addLayer(new TheLockLayerRenderer(
                    livingRenderer,
                    new TheLockLayerModel(modelRoot)));
        }
    }
}
