package rotp.addons.stone_legacy_zombie_orders.client;

import rotp.core.api.client.vampirism.HungryZombiePoseProviders;

import rotp.addons.stone_legacy_zombie_orders.ZombieOrdersAddon;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(
        modid = ZombieOrdersAddon.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class ZombieOrdersClientEvents {
    private ZombieOrdersClientEvents() {}

    @SubscribeEvent
    public static void clientSetup(
            FMLClientSetupEvent event) {
        event.enqueueWork(() ->
                HungryZombiePoseProviders.register(
                        ZombieOrdersAddon.internalId("waiting"),
                        zombie -> ZombieOrdersAddon
                                .state(zombie)
                                .activeWaitingPose()));
    }
}
