package rotp.addons.frosttraveler.client;

import rotp.addons.frosttraveler.FrostTravelerAddon;
import rotp.addons.frosttraveler.item.SprayerData;
import rotp.core.client.entityrender.stand.StandEntityRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(
        modid = FrostTravelerAddon.MOD_ID,
        bus = EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class FrostTravelerClientEvents {
    static final ModelResourceLocation SPRAYER_MODEL =
            ModelResourceLocation.standalone(
                    FrostTravelerAddon.id(
                            "item/cream_starter_base"));
    static final ModelResourceLocation SPRAYER_PKMN_MODEL =
            ModelResourceLocation.standalone(
                    FrostTravelerAddon.id(
                            "item/cream_starter_pkmn"));

    private FrostTravelerClientEvents() {}

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(
                FrostTravelerAddon.CREAM_STARTER_ITEM.get(),
                FrostTravelerAddon.id("pkmn_skin"),
                (stack, level, entity, seed) -> {
                    SprayerData data = stack.get(
                            FrostTravelerAddon.SPRAYER_DATA);
                    return data != null
                            && data.standSkin().endsWith(":pkmn")
                            ? 1.0F
                            : 0.0F;
                }));
    }

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                FrostTravelerAddon.CREAM_STARTER_ENTITY.get(),
                StandEntityRenderer::new);
        event.registerEntityRenderer(
                FrostTravelerAddon.SPRAY_BULLET_ENTITY.get(),
                CreamProjectileRenderer::new);
        event.registerEntityRenderer(
                FrostTravelerAddon.HEAL_SPRAY_ENTITY.get(),
                CreamProjectileRenderer::new);
    }

    @SubscribeEvent
    public static void registerClientExtensions(
            RegisterClientExtensionsEvent event) {
        event.registerItem(
                new CreamSprayerExtensions(),
                FrostTravelerAddon.CREAM_STARTER_ITEM);
    }

    @SubscribeEvent
    public static void registerAdditionalModels(
            ModelEvent.RegisterAdditional event) {
        event.register(SPRAYER_MODEL);
        event.register(SPRAYER_PKMN_MODEL);
    }

    @SubscribeEvent
    public static void registerParticles(
            RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(
                FrostTravelerAddon.MEAT_PARTICLE.get(),
                MeatParticle.Provider::new);
    }

    @SubscribeEvent
    public static void registerReloadListeners(
            RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(
                (ResourceManagerReloadListener)
                        FrostTravelerMaskRenderDiagnostics
                                ::onResourceReload);
    }

    @SubscribeEvent
    public static void addLayers(
            EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer != null) {
                DisguiseLayer layer = new DisguiseLayer(
                        renderer,
                        event.getEntityModels().bakeLayer(
                                ModelLayers.PLAYER),
                        event.getEntityModels().bakeLayer(
                                ModelLayers.PLAYER_SLIM));
                FrostTravelerMaskRenderDiagnostics
                        .recordLayerRegistration(
                                layer.getClass().getName());
                renderer.addLayer(layer);
            }
        }
    }
}
