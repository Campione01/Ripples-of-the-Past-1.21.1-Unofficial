package rotp.addons.sexpistols.client;

import java.util.Set;

import rotp.core.api.client.render.FirstPersonStandRenderPolicies;
import rotp.core.api.client.render.PlayerArmPoseProvider;
import rotp.core.api.client.render.PlayerArmPoseProviders;
import rotp.core.api.client.render.PlayerArmPoseQuery;
import rotp.core.api.client.render.PlayerBaseModelVisibilityPolicies;
import rotp.core.api.client.render.ScopedPlayerModelVisibility.Part;
import rotp.core.client.ui.marker.MarkerRenderer;

import rotp.addons.sexpistols.SexPistolsIds;
import rotp.addons.sexpistols.config.SexPistolsConfig;
import rotp.addons.sexpistols.content.revolver.item.RevolverItem;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsEntity;
import rotp.addons.sexpistols.network.RevolverVisualPayloads;
import rotp.addons.sexpistols.network.SexPistolsConfigSync;
import rotp.addons.sexpistols.registry.SexPistolsEntities;
import rotp.addons.sexpistols.registry.SexPistolsItems;
import rotp.addons.sexpistols.registry.SexPistolsParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

@EventBusSubscriber(
        modid = SexPistolsIds.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class SexPistolsClientEvents {
    private static final ResourceLocation REVOLVER_POSE_OWNER =
            SexPistolsIds.id("revolver_arm_pose");
    private static final ResourceLocation FIRST_PERSON_OWNER =
            SexPistolsIds.id("first_person_stand");
    private static final ResourceLocation MISTA_HAT_VISIBILITY_OWNER =
            SexPistolsIds.id("mista_hat_skin_overlay");

    private SexPistolsClientEvents() {}

    public static void registerConfigScreen(ModContainer modContainer) {
        modContainer.registerExtensionPoint(
                IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            SexPistolsClientConfigSnapshot.resetToLocal();
            SexPistolsConfigSync.installClientHandler(
                    SexPistolsClientConfigSnapshot::applyServer);
            PlayerArmPoseProviders.register(
                    REVOLVER_POSE_OWNER,
                    new PlayerArmPoseProvider() {
                        @Override
                        public HumanoidModel.ArmPose armPose(
                                PlayerArmPoseQuery query) {
                            return !query.player().swinging
                                            && query.stack()
                                                    .getItem()
                                                    instanceof
                                                    RevolverItem
                                    ? HumanoidModel.ArmPose
                                            .CROSSBOW_HOLD
                                    : null;
                        }
                    });
            FirstPersonStandRenderPolicies.register(
                    FIRST_PERSON_OWNER,
                    query -> query.stand()
                            instanceof SexPistolsEntity);
            MarkerRenderer.registerMarkerRenderer(
                    new SexPistolsMoveItemMarker(
                            Minecraft.getInstance()));
            RevolverVisualPayloads.installClientHandlers(
                    RevolverVisualClientHandler::spawnMuzzleFlash,
                    RevolverVisualClientHandler::ejectCasings,
                    RevolverVisualClientHandler::spawnKickFlash,
                    RevolverVisualClientHandler::spawnBulletHole);
        });
    }

    @SubscribeEvent
    public static void onConfigLoading(
            ModConfigEvent.Loading event) {
        refreshLocalConfig(event);
    }

    @SubscribeEvent
    public static void onConfigReloading(
            ModConfigEvent.Reloading event) {
        refreshLocalConfig(event);
    }

    @SubscribeEvent
    public static void onConfigUnloading(
            ModConfigEvent.Unloading event) {
        if (event.getConfig().getSpec()
                == SexPistolsConfig.SPEC) {
            SexPistolsClientConfigSnapshot.clearLocal();
        }
    }

    private static void refreshLocalConfig(
            ModConfigEvent event) {
        if (event.getConfig().getSpec()
                == SexPistolsConfig.SPEC) {
            SexPistolsClientConfigSnapshot.refreshLocal();
        }
    }

    @SubscribeEvent
    public static void registerParticleProviders(
            RegisterParticleProvidersEvent event) {
        event.registerSpecial(
                SexPistolsParticles.BULLET_HOLE.get(),
                new BulletHoleParticle.Factory());
        event.registerSpriteSet(
                SexPistolsParticles.REVOLVER_CASING.get(),
                RevolverCasingParticle.Factory::new);
        event.registerSpriteSet(
                SexPistolsParticles.REVOLVER_MUZZLE_FLASH.get(),
                RevolverMuzzleFlashParticle.Factory::new);
        event.registerSpriteSet(
                SexPistolsParticles.KICK_MUZZLE_FLASH.get(),
                sprites -> new RevolverMuzzleFlashParticle.Factory(
                        sprites,
                        RevolverMuzzleFlashParticle.FlashKind
                                .KICK_NORMAL));
        event.registerSpriteSet(
                SexPistolsParticles
                        .KICK_MUZZLE_FLASH_CRITICAL.get(),
                sprites -> new RevolverMuzzleFlashParticle.Factory(
                        sprites,
                        RevolverMuzzleFlashParticle.FlashKind
                                .KICK_CRITICAL));
    }

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        PlayerBaseModelVisibilityPolicies.register(
                MISTA_HAT_VISIBILITY_OWNER,
                query -> query.player()
                                .getItemBySlot(EquipmentSlot.HEAD)
                                .is(SexPistolsItems.MISTA_SUIT_HAT.get())
                        ? Set.of(Part.HAT)
                        : Set.of());
        SexPistolsEntities.members().forEach(member ->
                event.registerEntityRenderer(
                        member.get(),
                        SexPistolsRenderer::new));
        event.registerEntityRenderer(
                SexPistolsEntities.REVOLVER_BULLET.get(),
                RevolverBulletRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(
            EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(
                MistaHatModel.LAYER_LOCATION,
                MistaHatModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerClientExtensions(
            RegisterClientExtensionsEvent event) {
        event.registerItem(
                new MistaHatExtensions(),
                rotp.addons.sexpistols
                        .registry.SexPistolsItems.MISTA_SUIT_HAT);
        event.registerItem(
                new RevolverExtensions(),
                rotp.addons.sexpistols
                        .registry.SexPistolsItems.REVOLVER);
    }

    @SubscribeEvent
    public static void registerReloadListeners(
            RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(
                (ResourceManagerReloadListener)
                        RevolverRenderer::reload);
    }

    @SubscribeEvent
    public static void registerHud(
            RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                SexPistolsIds.id("sex_pistols_hud"),
                new SexPistolsHud());
    }
}
