package rotp.addons.stickyfingers.client;

import rotp.core.api.client.render.HumanoidModelPostSetup;

import rotp.addons.stickyfingers.StickyFingersAddon;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(
        modid = StickyFingersAddon.MOD_ID,
        value = Dist.CLIENT)
public final class StickyFingersClientHooks {
    private StickyFingersClientHooks() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> HumanoidModelPostSetup.register(
                StickyFingersAddon.id(
                        "detached_body_carry_pose"),
                StickyFingersBodyPresentation
                        ::applyFinalCarryPose));
    }
}
