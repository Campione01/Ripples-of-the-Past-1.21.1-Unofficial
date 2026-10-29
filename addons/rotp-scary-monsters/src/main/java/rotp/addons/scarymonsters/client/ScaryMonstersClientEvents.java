package rotp.addons.scarymonsters.client;

import rotp.core.api.client.time.ClientRegionalTimeDilationPolicies;
import rotp.core.api.client.time.ClientRegionalTimeDilationQuery;
import rotp.core.api.client.time.ClientRegionalTimeDilationSurface;

import rotp.addons.scarymonsters.ScaryMonstersAddon;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@EventBusSubscriber(
        modid = ScaryMonstersAddon.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class ScaryMonstersClientEvents {
    private ScaryMonstersClientEvents() {}

    public static void registerConfigScreen(ModContainer modContainer) {
        modContainer.registerExtensionPoint(
                IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ClientRegionalTimeDilationPolicies.register(
                ScaryMonstersAddon.id("dynamic_vision"),
                ScaryMonstersClientEvents::dynamicVisionDilation));
    }

    /**
     * 1.16 DynamicVisionClientHandler: while the vision is on, the client timer runs at 100 ms per tick instead of 50,
     * so the whole world moves at half speed around the user (the Speed VI it grants makes up for their own pace).
     * The core's TIMER surface is that timer; particles and sounds follow it as they did in 1.16.
     */
    private static float dynamicVisionDilation(ClientRegionalTimeDilationQuery query) {
        LocalPlayer player = query.localPlayer();
        return query.surface() == ClientRegionalTimeDilationSurface.TIMER
                && player != null
                && player.isAlive()
                && ScaryMonstersAddon.state(player).dynamicVision()
                ? 0.5F : 1.0F;
    }

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                ScaryMonstersAddon.STAND_ENTITY.get(),
                ScaryMonstersStandRenderer::new);
        event.registerEntityRenderer(
                ScaryMonstersAddon.DINOSAUR.get(),
                ScaryDinosaurRenderer::new);
        event.registerEntityRenderer(
                ScaryMonstersAddon.TEMPLATE_PICKAXE.get(),
                context -> new ThrownItemRenderer<>(context, 1.0F, true));
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(
            EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(
                ScaryMonstersSuitModel.SUIT_DEFAULT,
                ScaryMonstersSuitModel::createDefaultLayer);
        event.registerLayerDefinition(
                ScaryMonstersSuitModel.SUIT_SLIM,
                ScaryMonstersSuitModel::createSlimLayer);
        event.registerLayerDefinition(
                ScaryMonstersTailModel.LAYER,
                ScaryMonstersTailModel::createLayer);
    }

    /** 1.16 order: suit (WhiteAlbumLayer), dragon retexture (DragonLayer), tail (TailLayer). */
    @SubscribeEvent
    public static void addPlayerLayers(
            EntityRenderersEvent.AddLayers event) {
        EntityModelSet models = event.getEntityModels();
        for (PlayerSkin.Model skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            boolean slim = skin == PlayerSkin.Model.SLIM;
            renderer.addLayer(new ScaryMonstersSuitLayer(
                    renderer,
                    new ScaryMonstersSuitModel<>(
                            models.bakeLayer(slim
                                    ? ScaryMonstersSuitModel.SUIT_SLIM
                                    : ScaryMonstersSuitModel.SUIT_DEFAULT),
                            slim),
                    slim));
            renderer.addLayer(new DragonPlayerLayer(renderer, slim));
            renderer.addLayer(new ScaryMonstersTailLayer(
                    renderer,
                    new ScaryMonstersTailModel(
                            models.bakeLayer(ScaryMonstersTailModel.LAYER))));
        }
    }

    @SubscribeEvent
    public static void registerOverlay(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ScaryMonstersAddon.id("forms"),
                new ScaryMonstersOverlay());
    }
}
