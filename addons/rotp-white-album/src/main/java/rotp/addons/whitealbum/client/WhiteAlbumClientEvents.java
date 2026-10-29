package rotp.addons.whitealbum.client;

import java.util.OptionalInt;
import java.util.Set;

import rotp.core.api.client.render.AbilitySelectionVisualPolicies;
import rotp.core.api.client.render.PlayerBaseModelVisibilityPolicies;
import rotp.core.api.client.render.ScopedPlayerModelVisibility.Part;
import rotp.core.client.ClientGlobals;
import rotp.core.client.entityrender.entities.NoopEntityRenderer;
import rotp.core.impl.powers.hamon.client.particle.HamonSparkParticle;

import rotp.addons.whitealbum.WhiteAlbumAddon;
import rotp.addons.whitealbum.ability.FreezeFloorAbility;
import rotp.addons.whitealbum.ability.GentlyWeepsAbility;
import rotp.addons.whitealbum.entity.WhiteAlbumControllerEntity;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

@EventBusSubscriber(
        modid = WhiteAlbumAddon.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class WhiteAlbumClientEvents {
    private static final Set<Part> OUTER_SKIN_PARTS = Set.of(
            Part.HAT,
            Part.JACKET,
            Part.LEFT_SLEEVE,
            Part.RIGHT_SLEEVE,
            Part.LEFT_PANTS,
            Part.RIGHT_PANTS);

    private WhiteAlbumClientEvents() {}

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        if (ModList.get().isLoaded("epicfight")) {
            event.enqueueWork(
                    WhiteAlbumEpicFightCompat::register);
        }
    }

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        AbilitySelectionVisualPolicies.register(
                WhiteAlbumAddon.id("mode_selection"),
                query -> {
                    WhiteAlbumControllerEntity controller =
                            query.power() != null
                                    ? WhiteAlbumAddon.controller(
                                            query.power().getUser())
                                    : null;
                    if (controller == null) {
                        return OptionalInt.empty();
                    }
                    boolean active;
                    if (query.ability()
                            instanceof FreezeFloorAbility) {
                        active = controller.isFreezeFloorActive();
                    }
                    else if (query.ability()
                            instanceof GentlyWeepsAbility) {
                        active = controller.isGentlyWeepsActive();
                    }
                    else {
                        active = false;
                    }
                    return active
                            ? OptionalInt.of(0xFF00FF00)
                            : OptionalInt.empty();
                });
        PlayerBaseModelVisibilityPolicies.register(
                WhiteAlbumAddon.id("white_album_outer_skin"),
                query -> ClientGlobals.canSeeStands
                                && WhiteAlbumAddon.controller(
                                        query.player()) != null
                        ? OUTER_SKIN_PARTS
                        : Set.of());
        event.registerEntityRenderer(
                WhiteAlbumAddon
                        .WHITE_ALBUM_CONTROLLER.get(),
                NoopEntityRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(
            EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(
                WhiteAlbumWearableModel.SUIT_DEFAULT,
                WhiteAlbumWearableModel::createSuitDefaultLayer);
        event.registerLayerDefinition(
                WhiteAlbumWearableModel.SUIT_SLIM,
                WhiteAlbumWearableModel::createSuitSlimLayer);
        event.registerLayerDefinition(
                WhiteAlbumWearableModel.SHIELD_DEFAULT,
                WhiteAlbumWearableModel::createShieldDefaultLayer);
        event.registerLayerDefinition(
                WhiteAlbumWearableModel.SHIELD_SLIM,
                WhiteAlbumWearableModel::createShieldSlimLayer);
    }

    @SubscribeEvent
    public static void registerParticles(
            RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(
                WhiteAlbumAddon.ICE_SHARD.get(),
                HamonSparkParticle.HamonParticleFactory::new);
    }

    @SubscribeEvent
    public static void addLayers(
            EntityRenderersEvent.AddLayers event) {
        EntityModelSet models = event.getEntityModels();
        for (PlayerSkin.Model skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            addPlayerLayers(
                    renderer,
                    models,
                    skin == PlayerSkin.Model.SLIM);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addPlayerLayers(
            PlayerRenderer renderer,
            EntityModelSet models,
            boolean slim) {
        renderer.addLayer(new WhiteAlbumSuitLayer(
                renderer,
                new WhiteAlbumWearableModel<>(
                        models.bakeLayer(
                                slim
                                        ? WhiteAlbumWearableModel
                                                .SUIT_SLIM
                                        : WhiteAlbumWearableModel
                                                .SUIT_DEFAULT),
                        slim,
                        WhiteAlbumWearableModel.DonorLayer.SUIT),
                slim));
        renderer.addLayer(new WhiteAlbumShieldLayer(
                renderer,
                new WhiteAlbumWearableModel<>(
                        models.bakeLayer(
                                slim
                                        ? WhiteAlbumWearableModel
                                                .SHIELD_SLIM
                                        : WhiteAlbumWearableModel
                                                .SHIELD_DEFAULT),
                        slim,
                        WhiteAlbumWearableModel.DonorLayer.SHIELD),
                slim));
    }
}
