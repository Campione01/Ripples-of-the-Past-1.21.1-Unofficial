package rotp.addons.whitesnake;

import rotp.core.powersystem.standpower.entity.StandEntity;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@EventBusSubscriber(
        modid = WhitesnakeAddon.MOD_ID,
        bus = EventBusSubscriber.Bus.MOD)
public final class WhitesnakeEvents {
    private WhitesnakeEvents() {}

    @SubscribeEvent
    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                WhitesnakeAddon.WHITESNAKE_ENTITY.get(),
                StandEntity.createAttributes().build());
    }
}
