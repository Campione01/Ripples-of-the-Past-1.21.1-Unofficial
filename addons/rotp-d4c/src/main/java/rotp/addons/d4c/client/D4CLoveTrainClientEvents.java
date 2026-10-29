package rotp.addons.d4c.client;

import rotp.addons.d4c.D4CAddon;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

@EventBusSubscriber(
        modid = D4CAddon.MOD_ID,
        value = Dist.CLIENT)
public final class D4CLoveTrainClientEvents {
    private D4CLoveTrainClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        D4CLoveTrainClientPresentation.tick();
    }

    @SubscribeEvent
    public static void onLogout(
            ClientPlayerNetworkEvent.LoggingOut event) {
        D4CLoveTrainClientPresentation.clearAll();
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            D4CLoveTrainClientPresentation.clearAll();
        }
    }

    @SubscribeEvent
    public static void onEntityLeave(
            EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()
                && event.getEntity() instanceof Player player) {
            D4CLoveTrainClientPresentation.removeOwner(
                    player.getUUID());
        }
    }
}
