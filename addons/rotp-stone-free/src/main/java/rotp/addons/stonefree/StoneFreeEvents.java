package rotp.addons.stonefree;

import rotp.core.powersystem.standpower.entity.StandEntity;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@EventBusSubscriber(
        modid = StoneFreeAddon.MOD_ID,
        bus = EventBusSubscriber.Bus.MOD)
public final class StoneFreeEvents {
    private StoneFreeEvents() {}

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(
                StoneFreeAddon.STONE_FREE_ENTITY.get(),
                StandEntity.createAttributes().build());
    }
}
