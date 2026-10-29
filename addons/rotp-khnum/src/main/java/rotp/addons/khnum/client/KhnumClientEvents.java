package rotp.addons.khnum.client;

import rotp.core.client.entityrender.entities.NoopEntityRenderer;

import rotp.addons.khnum.KhnumAddon;
import rotp.addons.khnum.disguise.DisguiseClientBridge;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(
        modid = KhnumAddon.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class KhnumClientEvents {
    static {
        DisguiseClientBridge.install(
                DisguiseSelectionScreen::open);
    }

    private KhnumClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                KhnumAddon.CONTROLLER.get(),
                NoopEntityRenderer::new);
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
            addPlayerLayer(renderer, models);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addPlayerLayer(
            PlayerRenderer renderer,
            EntityModelSet models) {
        renderer.addLayer(new KhnumDisguiseLayer(
                renderer,
                models.bakeLayer(ModelLayers.PLAYER),
                models.bakeLayer(ModelLayers.PLAYER_SLIM)));
    }
}
