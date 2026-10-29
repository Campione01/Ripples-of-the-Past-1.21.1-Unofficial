package rotp.addons.vampire_climb.client;

import rotp.core.api.client.render.FirstPersonPostArmLayers;

import rotp.addons.vampire_climb.VampireClimbAddon;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(
        modid = VampireClimbAddon.MOD_ID,
        value = Dist.CLIENT)
public final class VampireClimbClientEvents {
    private VampireClimbClientEvents() {}

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() ->
                FirstPersonPostArmLayers.register(
                        VampireClimbAddon.id("climbing_arm"),
                        0,
                        VampireClimbArmLayer::render));
    }
}
