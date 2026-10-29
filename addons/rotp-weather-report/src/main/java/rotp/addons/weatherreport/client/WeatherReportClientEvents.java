package rotp.addons.weatherreport.client;

import rotp.core.client.entityrender.entities.NoopEntityRenderer;

import rotp.addons.weatherreport.WeatherReportAddon;
import rotp.addons.weatherreport.network.WeatherReportPayloads;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(
        modid = WeatherReportAddon.MOD_ID,
        value = Dist.CLIENT)
public final class WeatherReportClientEvents {
    private WeatherReportClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                WeatherReportAddon.WEATHER_REPORT_ENTITY.get(),
                WeatherReportStandRenderer::new);
        event.registerEntityRenderer(
                WeatherReportAddon.ICICLE_STRIKE_PROJECTILE.get(),
                WeatherReportIcicleRenderer::new);
        event.registerEntityRenderer(
                WeatherReportAddon.FUGA_SUMMONER.get(),
                NoopEntityRenderer::new);
        event.registerEntityRenderer(
                WeatherReportAddon.FUGA_PROJECTILE.get(),
                WeatherReportFugaProjectileRenderer::new);
        WeatherReportPayloads.installClientOpenHandler(
                WeatherReportClientPayloads::openSelection);
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(
            EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(
                WeatherReportCloudsModel.LAYER,
                WeatherReportCloudsModel::createLayer);
    }

    /** 1.16 hung the cloud-shield layer on both player renderers. */
    @SubscribeEvent
    public static void addLayers(
            EntityRenderersEvent.AddLayers event) {
        EntityModelSet models = event.getEntityModels();
        for (PlayerSkin.Model skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            renderer.addLayer(new WeatherReportCloudShieldLayer(
                    renderer,
                    new WeatherReportCloudsModel(
                            models.bakeLayer(WeatherReportCloudsModel.LAYER))));
        }
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(
                    WeatherReportAddon.BLOOD_PUDDLE.get(),
                    RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(
                    WeatherReportAddon.BLOOD_SPIKES.get(),
                    RenderType.cutout());
        });
    }
}
