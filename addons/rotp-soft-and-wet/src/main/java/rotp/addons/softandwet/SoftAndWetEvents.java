package rotp.addons.softandwet;

import rotp.core.powersystem.standpower.entity.StandEntity;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@EventBusSubscriber(
        modid = SoftAndWetAddon.MOD_ID,
        bus = EventBusSubscriber.Bus.MOD)
public final class SoftAndWetEvents {
    private SoftAndWetEvents() {}

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(
                SoftAndWetAddon.SOFT_AND_WET_ENTITY.get(),
                StandEntity.createAttributes().build());
    }
}
