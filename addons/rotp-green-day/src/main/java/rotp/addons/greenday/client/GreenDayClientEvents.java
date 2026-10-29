package rotp.addons.greenday.client;

import java.util.OptionalInt;

import rotp.core.api.client.render.AbilitySelectionVisualPolicies;
import rotp.core.api.client.render.FirstPersonPostArmLayers;
import rotp.core.client.entityrender.stand.StandEntityRenderer;
import rotp.core.client.ui.marker.MarkerRenderer;

import rotp.addons.greenday.GreenDayAddon;
import rotp.addons.greenday.GreenDaySelectionPredicates;
import rotp.addons.greenday.state.GreenDayMoldState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

@EventBusSubscriber(
        modid = GreenDayAddon.MOD_ID,
        value = Dist.CLIENT)
public final class GreenDayClientEvents {
    private GreenDayClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                GreenDayAddon.GREEN_DAY_ENTITY.get(),
                StandEntityRenderer::new);
        event.registerEntityRenderer(
                GreenDayAddon.SHOOTING_ARM.get(),
                ShootingArmRenderer::new);
    }

    // 1.16's Green Day add-on added its MoldLayer to the renderers; the same here, so the overlay draws inside the
    // entity's transform.
    @SubscribeEvent
    public static void addLayers(
            EntityRenderersEvent.AddLayers event) {
        for (var entityType : event.getEntityTypes()) {
            addMoldLayer(event.getRenderer(entityType));
        }
        for (var skin : event.getSkins()) {
            addMoldLayer(event.getSkin(skin));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addMoldLayer(EntityRenderer<?> renderer) {
        if (renderer
                instanceof LivingEntityRenderer livingRenderer) {
            livingRenderer.addLayer(
                    new GreenDayMoldOverlayRenderer.Layer(
                            livingRenderer));
        }
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
                FirstPersonPostArmLayers.register(
                        GreenDayAddon.id("mold_hand"),
                        0,
                        GreenDayMoldOverlayRenderer
                                ::renderFirstPersonMold);
                MarkerRenderer.registerMarkerRenderer(
                        new GreenDayArmMarker(
                                Minecraft.getInstance()));
        });
    }

    @SubscribeEvent
    public static void registerSelectionPolicy(
            RegisterGuiLayersEvent event) {
        AbilitySelectionVisualPolicies.register(
                GreenDayAddon.id("mold_selection"),
                query -> {
                    boolean moldActive = query.power() != null
                            && GreenDayMoldState.get(
                                    query.power().getUser())
                                    .moldActive();
                    return GreenDaySelectionPredicates
                            .donorGreenSelection(
                                    query.ability()
                                            .getAbilityId()
                                            .nameInMoveset(),
                                    moldActive)
                            ? OptionalInt.of(0xFF00FF00)
                            : OptionalInt.empty();
                });
    }

}
