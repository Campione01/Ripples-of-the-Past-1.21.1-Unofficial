package rotp.addons.frosttraveler.client;

import rotp.core.api.client.render.ScopedPlayerModelVisibility;
import rotp.addons.frosttraveler.FrostTravelerAddon;
import rotp.addons.frosttraveler.item.DisguiseData;
import rotp.addons.frosttraveler.state.CreamStarterService;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.common.util.TriState;

@EventBusSubscriber(
        modid = FrostTravelerAddon.MOD_ID,
        value = Dist.CLIENT)
public final class FrostTravelerClientGameEvents {
    private FrostTravelerClientGameEvents() {}

    @SubscribeEvent
    public static void onRenderFramePre(RenderFrameEvent.Pre event) {
        FrostTravelerMaskRenderDiagnostics.beginFrame();
    }

    @SubscribeEvent
    public static void onRenderFramePost(RenderFrameEvent.Post event) {
        FrostTravelerMaskRenderDiagnostics.endFrame();
    }

    @SubscribeEvent
    public static void onNameTag(RenderNameTagEvent event) {
        if (!(event.getEntity()
                instanceof AbstractClientPlayer player)) {
            return;
        }
        DisguiseData data =
                CreamStarterService.equippedDisguise(player);
        if (data == null) {
            return;
        }
        if (data.targetUuid().isPresent()
                && !data.targetName().isBlank()) {
            event.setContent(Component.literal(
                    data.targetName()));
        } else if (data.targetType().isPresent()) {
            event.setCanRender(TriState.FALSE);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void hidePlayer(RenderPlayerEvent.Pre event) {
        if (!(event.getEntity()
                instanceof AbstractClientPlayer player)) {
            return;
        }
        FrostTravelerMaskRenderDiagnostics.recordPlayerPre(
                player, event.getRenderer().getModel());
        if (!DisguiseLayer.canRender(
                player, event.getPartialTick())) {
            return;
        }
        ScopedPlayerModelVisibility.hideForCurrentRender(
                player, event.getRenderer().getModel());
        FrostTravelerMaskRenderDiagnostics
                .recordScopedHideRequest(player);
    }

    @SubscribeEvent(
            priority = EventPriority.HIGH,
            receiveCanceled = true)
    public static void afterPlayerRender(RenderPlayerEvent.Post event) {
        if (event.getEntity()
                instanceof AbstractClientPlayer player) {
            FrostTravelerMaskRenderDiagnostics.recordPlayerPost(
                    player, event.getRenderer().getModel());
        }
    }

    @SubscribeEvent
    public static void onLogout(
            ClientPlayerNetworkEvent.LoggingOut event) {
        DisguiseLayer.clearCache();
        FrostTravelerMaskRenderDiagnostics.clear();
    }
}
