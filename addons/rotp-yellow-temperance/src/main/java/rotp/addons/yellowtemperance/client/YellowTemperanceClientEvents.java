package rotp.addons.yellowtemperance.client;

import rotp.core.client.entityrender.entities.NoopEntityRenderer;

import rotp.addons.yellowtemperance.YellowTemperanceAddon;
import rotp.addons.yellowtemperance.disguise.DisguiseClientBridge;
import rotp.addons.yellowtemperance.network.ErosionVisualBridge;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(
        modid = YellowTemperanceAddon.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class YellowTemperanceClientEvents {
    static {
        DisguiseClientBridge.install(
                DisguiseSelectionScreen::open);
        ErosionVisualBridge.install(
                ErosionVisualState::apply);
    }

    private YellowTemperanceClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                YellowTemperanceAddon.CONTROLLER.get(),
                NoopEntityRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(
            EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(
                YellowTemperanceWearableModel.DEFAULT,
                YellowTemperanceWearableModel::createDefaultLayer);
        event.registerLayerDefinition(
                YellowTemperanceWearableModel.SLIM,
                YellowTemperanceWearableModel::createSlimLayer);
    }

    @SubscribeEvent
    public static void addLayers(
            EntityRenderersEvent.AddLayers event) {
        EntityModelSet models = event.getEntityModels();
        for (PlayerSkin.Model skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer == null) {
                continue;
            }
            addPlayerLayers(
                    renderer,
                    models,
                    skin == PlayerSkin.Model.SLIM);
        }
    }

    @SubscribeEvent
    public static void registerHud(
            RegisterGuiLayersEvent event) {
        event.wrapLayer(
                VanillaGuiLayers.FOOD_LEVEL,
                YellowTemperanceFoodHud::new);
        event.registerAboveAll(
                YellowTemperanceAddon.id("doll_hud"),
                new YellowTemperanceDollHud());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addPlayerLayers(
            PlayerRenderer renderer,
            EntityModelSet models,
            boolean slim) {
        renderer.addLayer(new YellowTemperanceSuitLayer(
                renderer,
                new YellowTemperanceWearableModel<>(
                        models.bakeLayer(
                                slim
                                        ? YellowTemperanceWearableModel
                                                .SLIM
                                        : YellowTemperanceWearableModel
                                                .DEFAULT),
                        slim),
                slim));
        renderer.addLayer(new YellowTemperanceDisguiseLayer(
                renderer,
                models.bakeLayer(ModelLayers.PLAYER),
                models.bakeLayer(ModelLayers.PLAYER_SLIM)));
    }
}
