package rotp.addons.badcompany.client;

import rotp.core.client.ClientGlobals;
import rotp.core.client.entityrender.entities.NoopEntityRenderer;
import rotp.core.subsystems.entity_puppetcontrol.client.ClientEntityController;

import rotp.addons.badcompany.BadCompanyAddon;
import rotp.addons.badcompany.entity.BadCompanyStandEntity;
import rotp.addons.badcompany.network.BadCompanySelectionPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;

@EventBusSubscriber(
        modid = BadCompanyAddon.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class BadCompanyClientEvents {
    private BadCompanyClientEvents() {}

    /**
     * The soldiers' gun is a Stand item: as in the 1.16 addon it switches to an empty model for anyone who cannot see
     * Stands, so a dropped or framed gun stays hidden like the soldiers that carry it.
     */
    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(
                    BadCompanyAddon.BAD_GUN.get(),
                    BadCompanyAddon.id("stand_invisible"),
                    (stack, level, entity, seed) -> ClientGlobals.canSeeStands ? 0.0F : 1.0F);
            NeoForge.EVENT_BUS.addListener(BadCompanyClientEvents::onRenderNameTag);
        });
    }

    private static void onRenderNameTag(RenderNameTagEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || event.getEntity() != minecraft.player) {
            return;
        }
        Entity camera = minecraft.getCameraEntity();
        ClientEntityController controller = ClientEntityController.getInstance();
        boolean activeOwnedCamera = camera instanceof BadCompanyStandEntity coordinator
                && coordinator.isAlive()
                && coordinator.level() == minecraft.level
                && coordinator.getUser() == minecraft.player
                && coordinator.isActiveFor(minecraft.player)
                && coordinator.isManuallyControlled();
        if (shouldSuppressOwnerNameplate(event.getEntity(), minecraft.player, camera,
                controller != null ? controller.entity : null, activeOwnedCamera)) {
            event.setCanRender(TriState.FALSE);
        }
    }

    static boolean shouldSuppressOwnerNameplate(
            Object renderedEntity, Object localPlayer, Object camera, Object controlledEntity,
            boolean activeOwnedCamera) {
        return localPlayer != null && renderedEntity == localPlayer
                && camera != null && camera != localPlayer && camera == controlledEntity
                && activeOwnedCamera;
    }

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                BadCompanyAddon.BAD_COMPANY_ENTITY.get(),
                NoopEntityRenderer::new);
        event.registerEntityRenderer(
                BadCompanyAddon.BAD_SOLDIER.get(),
                BadCompanySoldierRenderer::new);
        event.registerEntityRenderer(
                BadCompanyAddon.BAD_TANK.get(),
                BadCompanyTankRenderer::new);
        event.registerEntityRenderer(
                BadCompanyAddon.BAD_HELICOPTER.get(),
                BadCompanyHelicopterRenderer::new);
        event.registerEntityRenderer(
                BadCompanyAddon.BAD_BULLET.get(),
                BadCompanyBulletRenderer::new);
        event.registerEntityRenderer(
                BadCompanyAddon.BAD_MISSILE.get(),
                BadCompanyMissileRenderer::new);
        event.registerEntityRenderer(
                BadCompanyAddon.BAD_MINE.get(),
                BadCompanyMineRenderer::new);
        BadCompanySelectionPayloads.installClientOpenHandler(
                BadCompanyClientPayloads::openSelection);
    }

    @SubscribeEvent
    public static void registerGuiLayers(
            RegisterGuiLayersEvent event) {
        // 1.16 drew it after the helmet overlay (RenderGameOverlayEvent.Post HELMET): over the pumpkin/vignette
        // camera overlays, under the crosshair, hotbar and chat
        event.registerAbove(
                VanillaGuiLayers.CAMERA_OVERLAYS,
                BadCompanyAddon.id("remote_overlay"),
                new BadCompanyRemoteHud());
    }

    @SubscribeEvent
    public static void registerLayers(
            EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(
                BadCompanyTankModel.LAYER,
                BadCompanyTankModel::createLayer);
        event.registerLayerDefinition(
                BadCompanyHelicopterModel.LAYER,
                BadCompanyHelicopterModel::createLayer);
        event.registerLayerDefinition(
                BadCompanyMissileModel.LAYER,
                BadCompanyMissileModel::createLayer);
        event.registerLayerDefinition(
                BadCompanyMineModel.LAYER,
                BadCompanyMineModel::createLayer);
        event.registerLayerDefinition(
                BadCompanyParachuteModel.LAYER,
                BadCompanyParachuteModel::createLayer);
    }
}
