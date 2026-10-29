package rotp.addons.purplehaze;

import rotp.core.powersystem.standpower.entity.StandEntity;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@EventBusSubscriber(
        modid = PurpleHazeAddon.MOD_ID,
        bus = EventBusSubscriber.Bus.MOD)
public final class PurpleHazeEvents {
    private PurpleHazeEvents() {}

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(
                PurpleHazeAddon.PURPLE_HAZE_ENTITY.get(),
                StandEntity.createAttributes().build());
    }
}
