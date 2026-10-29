package rotp.addons.whitesnake.standdisc;

import rotp.addons.whitesnake.WhitesnakeAddon;
import rotp.addons.whitesnake.disc.StandDiscAuthorityData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = WhitesnakeAddon.MOD_ID)
public final class WhitesnakeStandDiscEvents {
    private WhitesnakeStandDiscEvents() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(
            PlayerInteractEvent.RightClickItem event) {
        if (StandDiscAuthorityData.get(
                event.getItemStack()).isEmpty()) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        if (event.getLevel().isClientSide()) {
            event.getEntity().displayClientMessage(
                    Component.translatable(
                            "rotp_whitesnake.stand_disc.bound_use"),
                    true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerClone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        Player replacement = event.getEntity();
        if (replacement.level().isClientSide()) {
            return;
        }
        WhitesnakeAddon.standDiscState(replacement).copyFrom(
                WhitesnakeAddon.standDiscState(original));
    }
}
