package rotp.addons.whitesnake.client;

import rotp.core.api.client.render.FirstPersonPostArmLayers;

import rotp.addons.whitesnake.WhitesnakeAddon;
import rotp.addons.whitesnake.disguise.DisguiseClientBridge;
import rotp.addons.whitesnake.disc.StandDiscAuthorityData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = WhitesnakeAddon.MOD_ID, value = Dist.CLIENT)
public final class WhitesnakeClientEvents {
    static {
        DisguiseClientBridge.install(DisguiseSelectionScreen::open);
    }

    private WhitesnakeClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                WhitesnakeAddon.WHITESNAKE_ENTITY.get(),
                WhitesnakeRenderer::new);
        event.registerEntityRenderer(
                WhitesnakeAddon.DISC_ENTITY.get(),
                DiscRenderer::new);
        event.registerEntityRenderer(
                WhitesnakeAddon.MELT_HEART_PROJECTILE.get(),
                MeltHeartProjectileRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(
            EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(
                DiscModel.LAYER_LOCATION,
                DiscModel::createBodyLayer);
        event.registerLayerDefinition(
                MeltHeartProjectileModel.LAYER_LOCATION,
                MeltHeartProjectileModel::createBodyLayer);
    }

    // 1.16 add-ons hung body overlays on the renderers themselves; the melt overlay is a RenderLayer here for the
    // same reason, so it draws inside the entity's transform.
    @SubscribeEvent
    public static void addLayers(
            EntityRenderersEvent.AddLayers event) {
        for (var entityType : event.getEntityTypes()) {
            addMeltLayer(event.getRenderer(entityType));
        }
        for (var skin : event.getSkins()) {
            addMeltLayer(event.getSkin(skin));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addMeltLayer(EntityRenderer<?> renderer) {
        if (renderer
                instanceof LivingEntityRenderer livingRenderer) {
            livingRenderer.addLayer(
                    new MeltHeartOverlayRenderer.Layer(
                            livingRenderer));
        }
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() ->
                FirstPersonPostArmLayers.register(
                        WhitesnakeAddon.id("melt_heart"),
                        0,
                        MeltHeartOverlayRenderer::renderFirstPerson));
    }

    @SubscribeEvent
    public static void addStandDiscAuthorityTooltip(
            ItemTooltipEvent event) {
        StandDiscAuthorityData.get(event.getItemStack())
                .ifPresent(data -> {
                    event.getToolTip().add(Component.translatable(
                                    "rotp_whitesnake.stand_disc.owner",
                                    data.ownerName())
                            .withStyle(ChatFormatting.GRAY));
                    event.getToolTip().add(Component.translatable(
                                    "rotp_whitesnake.stand_disc.bound_hint")
                            .withStyle(ChatFormatting.DARK_GRAY));
                });
    }
}
