package rotp.addons.mandom.client;

import rotp.core.client.ui.StandStatsRenderer;
import rotp.addons.mandom.MandomAddon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(
        modid = MandomAddon.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class MandomClientEvents {
    private MandomClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                MandomAddon.MANDOM_ENTITY.get(),
                MandomControllerRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(
            EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(
                MandomAttachmentModel.LAYER_LOCATION,
                MandomAttachmentModel::createBodyLayer);
        event.registerLayerDefinition(
                RingoClockModel.LAYER_LOCATION,
                RingoClockModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void addLayers(
            EntityRenderersEvent.AddLayers event) {
        MandomUserLayer.installFirstPersonModel(
                new MandomAttachmentModel(
                        event.getEntityModels().bakeLayer(
                                MandomAttachmentModel.LAYER_LOCATION)));
        for (var entityType : event.getEntityTypes()) {
            addLivingLayer(
                    event.getRenderer(entityType),
                    event.getEntityModels().bakeLayer(
                            MandomAttachmentModel.LAYER_LOCATION));
        }
        for (var skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            addLivingLayer(
                    renderer,
                    event.getEntityModels().bakeLayer(
                            MandomAttachmentModel.LAYER_LOCATION));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addLivingLayer(
            EntityRenderer<?> renderer,
            ModelPart modelRoot) {
        if (renderer instanceof LivingEntityRenderer livingRenderer) {
            livingRenderer.addLayer(new MandomUserLayer(
                    livingRenderer,
                    new MandomAttachmentModel(modelRoot)));
        }
    }

    @SubscribeEvent
    public static void registerClientExtensions(
            RegisterClientExtensionsEvent event) {
        event.registerItem(
                new RingoClockExtensions(),
                MandomAddon.RINGO_CLOCK);
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() ->
                StandStatsRenderer.overrideCosmeticStats(
                        MandomAddon.id("mandom"),
                        new MandomCosmeticStats()));
    }

    @EventBusSubscriber(
            modid = MandomAddon.MOD_ID,
            value = Dist.CLIENT,
            bus = EventBusSubscriber.Bus.GAME)
    public static final class GameBusEvents {
        private GameBusEvents() {}

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void renderFirstPersonAttachment(
                RenderHandEvent event) {
            Minecraft minecraft = Minecraft.getInstance();
            LocalPlayer player = minecraft.player;
            if (event.isCanceled()
                    || player == null
                    || minecraft.getCameraEntity() != player
                    || !minecraft.options.getCameraType().isFirstPerson()
                    || !MandomAddon.isMandomUser(player)) {
                return;
            }
            HumanoidArm arm =
                    event.getHand() == InteractionHand.MAIN_HAND
                            ? player.getMainArm()
                            : player.getMainArm().getOpposite();
            if (arm == HumanoidArm.RIGHT) {
                MandomUserLayer.renderFirstPersonAttachment(
                        event, player);
            }
        }
    }
}
