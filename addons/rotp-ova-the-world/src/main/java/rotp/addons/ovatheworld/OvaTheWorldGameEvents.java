package rotp.addons.ovatheworld;

import rotp.core.api.timestop.TimeStopLifecycleEvent;
import rotp.core.subsystems.timestop.TimeStopLearning;
import rotp.core.subsystems.timestop.TimeStopState;

import rotp.addons.ovatheworld.ability.OvaTimeStopAbility;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

// The resume voices are the RESUME_VOICE cue of the OVA time-stop policy
// (OvaTheWorldAddon), which the core times as 1.16 did.
@EventBusSubscriber(modid = OvaTheWorldAddon.MOD_ID)
public final class OvaTheWorldGameEvents {
    private OvaTheWorldGameEvents() {}

    // 1.16 staminaCostTick(7.5) against the core's 9, both scaled by
    // 100 / the stop's trained ticks. The 250 start is the OVA time-stop
    // policy in OvaTheWorldAddon.
    @SubscribeEvent
    public static void onTimeStopPreStart(
            TimeStopLifecycleEvent.PreStart event) {
        if (!isOvaTimeStop(event)) {
            return;
        }
        TimeStopState.Instance instance = event.getInstance();
        event.setInstance(
                instance
                        .withVisualRoute("the_world_time_stop")
                        .withStaminaCostTick(
                                instance.staminaCostTick()
                                        * OvaTimeStopAbility
                                                .LEGACY_STAMINA_PER_TICK
                                        / TimeStopLearning
                                                .BASE_STAMINA_COST_TICK));
    }

    private static boolean isOvaTimeStop(
            TimeStopLifecycleEvent event) {
        return event.getInstance().standTypeId().isPresent()
                && event.getInstance().standTypeId().get()
                        .equals(OvaTheWorldAddon.id(
                                "the_world_ova"));
    }
}
