package rotp.addons.killerqueenlion.client;

import rotp.core.client.entityrender.stand.StandEntityRenderer;
import rotp.core.client.ui.marker.MarkerRenderer;

import rotp.addons.killerqueenlion.KillerQueenLionAddon;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(
        modid = KillerQueenLionAddon.MOD_ID,
        value = Dist.CLIENT)
public final class KillerQueenClientEvents {
    private KillerQueenClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                KillerQueenLionAddon.KILLER_QUEEN_ENTITY.get(),
                StandEntityRenderer::new);
        event.registerEntityRenderer(
                KillerQueenLionAddon.SNOW_BOMB_ENTITY.get(),
                SnowBombRenderer::new);
        event.registerEntityRenderer(
                KillerQueenLionAddon.BUBBLE_BOMB_ENTITY.get(),
                BubbleBombRenderer::new);
        event.registerEntityRenderer(
                KillerQueenLionAddon.BUBBLE_MARK_ENTITY.get(),
                BubbleMarkRenderer::new);
        event.registerEntityRenderer(
                KillerQueenLionAddon.SHEER_HEART_ENTITY.get(),
                SheerHeartAttackRenderer::new);
        event.registerEntityRenderer(
                KillerQueenLionAddon.PRIMARY_BOMB_MARKER_ENTITY.get(),
                PrimaryBombMarkerRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayers(
            EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(
                SnowBombRenderer.LAYER,
                KillerQueenProjectileModel::createLayer);
        event.registerLayerDefinition(
                BubbleBombRenderer.LAYER,
                KillerQueenProjectileModel::createLayer);
        event.registerLayerDefinition(
                BubbleMarkRenderer.LAYER,
                BubbleMarkModel::createLayer);
        event.registerLayerDefinition(
                SheerHeartAttackModel.LAYER,
                SheerHeartAttackModel::createLayer);
        event.registerLayerDefinition(
                PrimaryBombMarkerModel.LAYER,
                PrimaryBombMarkerModel::createLayer);
    }

    @SubscribeEvent
    public static void registerGuiLayers(
            RegisterGuiLayersEvent event) {
        // 1.16 (BitesZaDust) drew it after the helmet overlay: under the boss bar, hotbar and chat, not over them
        event.registerAbove(
                VanillaGuiLayers.CAMERA_OVERLAYS,
                KillerQueenLionAddon.id("bites_the_dust_countdown"),
                new BitesTheDustCountdownOverlay());
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
                MarkerRenderer.registerMarkerRenderer(
                        new BitesTheDustMarker(
                                Minecraft.getInstance()));
                MarkerRenderer.registerMarkerRenderer(
                        new BubbleBlockMarkMarker(
                                Minecraft.getInstance()));
        });
    }
}
