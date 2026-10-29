package rotp.addons.killerqueen.btd;

import rotp.core.api.timestop.TimeStopLifecycleEvent;

import rotp.addons.killerqueen.KillerQueenAddon;
import rotp.addons.killerqueen.state.KillerQueenStateService;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = KillerQueenAddon.MOD_ID)
public final class BitesTheDustLifecycle {
    private BitesTheDustLifecycle() {}

    @SubscribeEvent
    public static void onTimeStopAdded(
            TimeStopLifecycleEvent.Added event) {
        Entity user = event.getLevel().getEntity(
                event.getInstance().userId());
        if (user instanceof ServerPlayer owner
                && KillerQueenAddon.isKillerQueenUser(owner)
                && KillerQueenAddon.state(owner)
                        .bitesTheDustArmed()) {
            KillerQueenStateService.linkTimeStop(
                    owner, event.getInstance().id());
        }
    }

    @SubscribeEvent
    public static void onTimeStopRemoved(
            TimeStopLifecycleEvent.Removed event) {
        Entity user = event.getLevel().getEntity(
                event.getInstance().userId());
        if (user instanceof ServerPlayer owner) {
            KillerQueenStateService.unlinkTimeStop(
                    owner, event.getInstance().id());
        }
    }
}
