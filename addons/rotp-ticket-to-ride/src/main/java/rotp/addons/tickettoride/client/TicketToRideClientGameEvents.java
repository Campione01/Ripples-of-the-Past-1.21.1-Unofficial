package rotp.addons.tickettoride.client;

import rotp.addons.tickettoride.TicketToRideAddon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

@EventBusSubscriber(
        modid = TicketToRideAddon.MOD_ID,
        value = Dist.CLIENT)
public final class TicketToRideClientGameEvents {
    private TicketToRideClientGameEvents() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderHand(RenderHandEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (!event.isCanceled()
                && player != null
                && player.getItemInHand(event.getHand()).is(
                        TicketToRideAddon.TEARS_BLADE.get())) {
            TicketToRideClientDiagnostics.recordHandRender(
                    player, event.getHand());
        }
    }

    @SubscribeEvent
    public static void onRenderPlayer(
            RenderPlayerEvent.Post event) {
        Player player = event.getEntity();
        if (player.getMainHandItem().is(
                        TicketToRideAddon.TEARS_BLADE.get())
                || player.getOffhandItem().is(
                        TicketToRideAddon.TEARS_BLADE.get())) {
            TicketToRideClientDiagnostics
                    .recordThirdPersonPass(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(
            ClientPlayerNetworkEvent.LoggingOut event) {
        TicketToRideClientDiagnostics.clearReceipts();
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            TicketToRideClientDiagnostics.clearReceipts();
        }
    }
}
