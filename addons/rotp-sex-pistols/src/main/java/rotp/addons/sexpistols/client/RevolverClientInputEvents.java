package rotp.addons.sexpistols.client;

import rotp.addons.sexpistols.SexPistolsIds;
import rotp.addons.sexpistols.content.revolver.item.RevolverItem;
import rotp.addons.sexpistols.network.RevolverChamberControlPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(
        modid = SexPistolsIds.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.GAME)
public final class RevolverClientInputEvents {
    private RevolverClientInputEvents() {}

    // 1.16's revolver click ran at HIGHEST, before the core's click handling. NORMAL keeps the core's ability press
    // (also NORMAL, registered by the core mod first), which cancels the click it handled, ahead of the revolver, and
    // runs ahead of the core's LOW cancel of a click during a player action, which would leave the cylinder open.
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onInteraction(
            InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isCanceled()
                || !event.isAttack()
                || event.getHand() != InteractionHand.MAIN_HAND
                || !requestCloseCylinder()) {
            return;
        }
        event.setSwingHand(false);
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onMouseScroll(
            InputEvent.MouseScrollingEvent event) {
        double delta = event.getScrollDeltaY();
        if (event.isCanceled()
                || delta == 0.0D
                || Minecraft.getInstance().screen != null
                || !requestSwitchCylinder(delta > 0.0D)) {
            return;
        }
        event.setCanceled(true);
    }

    public static boolean requestCloseCylinder() {
        if (!hasOpenMainHandRevolver()) {
            return false;
        }
        PacketDistributor.sendToServer(
                new RevolverChamberControlPayload(
                        RevolverChamberControlPayload.CLOSE_CYLINDER));
        return true;
    }

    public static boolean requestSwitchCylinder(boolean forward) {
        if (!hasOpenMainHandRevolver()) {
            return false;
        }
        PacketDistributor.sendToServer(
                new RevolverChamberControlPayload(
                        forward
                                ? RevolverChamberControlPayload
                                        .SWITCH_FORWARD
                                : RevolverChamberControlPayload
                                        .SWITCH_BACKWARD));
        return true;
    }

    private static boolean hasOpenMainHandRevolver() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        ItemStack stack = player.getMainHandItem();
        return stack.getItem() instanceof RevolverItem
                && RevolverItem.state(stack).reloadMode();
    }
}
