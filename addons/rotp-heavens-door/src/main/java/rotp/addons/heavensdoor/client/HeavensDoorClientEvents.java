package rotp.addons.heavensdoor.client;

import rotp.core.api.client.render.FirstPersonPostArmLayers;
import rotp.core.client.entityrender.stand.StandEntityRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import rotp.core.client.ui.marker.MarkerRenderer;

import rotp.addons.heavensdoor.HeavensDoorAddon;
import rotp.addons.heavensdoor.network.OpenBookSnapshotPayload;
import rotp.addons.heavensdoor.network.BookStateSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(
        modid = HeavensDoorAddon.MOD_ID,
        value = Dist.CLIENT)
public final class HeavensDoorClientEvents {
    private HeavensDoorClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                HeavensDoorAddon.HEAVENS_DOOR_ENTITY.get(),
                StandEntityRenderer::new);
    }

    // 1.16's Heaven's Door added its BookLayer to the renderers; the same here, so the pages draw inside the
    // entity's transform.
    @SubscribeEvent
    public static void addLayers(
            EntityRenderersEvent.AddLayers event) {
        for (var entityType : event.getEntityTypes()) {
            addBookLayer(event.getRenderer(entityType));
        }
        for (var skin : event.getSkins()) {
            addBookLayer(event.getSkin(skin));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addBookLayer(EntityRenderer<?> renderer) {
        if (renderer
                instanceof LivingEntityRenderer livingRenderer) {
            livingRenderer.addLayer(
                    new HeavensDoorBookOverlayRenderer.Layer(
                            livingRenderer));
        }
    }

    // 1.16 shipped icons for book_effect and kys_effect only; every other
    // effect returned false from shouldRender and shouldRenderHUD.
    @SubscribeEvent
    public static void registerHiddenEffects(
            RegisterClientExtensionsEvent event) {
        event.registerMobEffect(
                new IClientMobEffectExtensions() {
                    @Override
                    public boolean isVisibleInInventory(
                            MobEffectInstance instance) {
                        return false;
                    }

                    @Override
                    public boolean isVisibleInGui(
                            MobEffectInstance instance) {
                        return false;
                    }
                },
                HeavensDoorAddon.BLINDNESS_ENTITY_EFFECT.get(),
                HeavensDoorAddon.CANNOT_MOVE.get(),
                HeavensDoorAddon.FORGOT_ATTACK.get(),
                HeavensDoorAddon.FORGOT_SECOND_HAND.get(),
                HeavensDoorAddon.FORGOT_SWIM.get(),
                HeavensDoorAddon.FORGOT_RUN.get(),
                HeavensDoorAddon.FORGOT_JUMP.get(),
                HeavensDoorAddon.FORGOT_BREAK.get(),
                HeavensDoorAddon.FORGOT_CRAFT.get(),
                HeavensDoorAddon.FORGOT_STAND.get(),
                HeavensDoorAddon.FORGOT_CONTAINERS.get(),
                HeavensDoorAddon.FORGOT_SHIFT.get());
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
                FirstPersonPostArmLayers.register(
                        HeavensDoorAddon.id("book_hand"),
                        0,
                        HeavensDoorBookOverlayRenderer
                                ::renderFirstPersonBook);
                OpenBookSnapshotPayload.installClientOpenHandler(
                        payload -> Minecraft.getInstance().execute(
                                () -> Minecraft.getInstance().setScreen(
                                        new HeavensDoorBookScreen(
                                                payload))));
                BookStateSyncPayload.installClientUpdateHandler(payload -> {
                    if (!payload.active()) {
                        var screen = Minecraft.getInstance().screen;
                        if (screen instanceof HeavensDoorBookScreen book) {
                            book.closeFromServer(payload.entityId());
                        }
                        else if (screen instanceof HeavensDoorPlayerSelectionScreen selection) {
                            selection.closeFromServer(payload.entityId());
                        }
                    }
                });
                MarkerRenderer.registerMarkerRenderer(
                        new HeavensDoorBookMarkerRenderer(
                                Minecraft.getInstance()));
        });
    }
}
