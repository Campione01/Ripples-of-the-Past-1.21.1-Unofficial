package rotp.addons.novemberrain;

import rotp.core.powersystem.standpower.entity.StandEntity;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@EventBusSubscriber(
        modid = NovemberRainAddon.MOD_ID,
        bus = EventBusSubscriber.Bus.MOD)
public final class NovemberRainEvents {
    private NovemberRainEvents() {}

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(
                NovemberRainAddon.NOVEMBER_RAIN_ENTITY.get(),
                StandEntity.createAttributes().build());
    }
}
