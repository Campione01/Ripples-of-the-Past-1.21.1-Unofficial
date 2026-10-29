package rotp.addons.greenday;

import rotp.core.powersystem.standpower.entity.StandEntity;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@EventBusSubscriber(
        modid = GreenDayAddon.MOD_ID)
public final class GreenDayModEvents {
    private GreenDayModEvents() {}

    @SubscribeEvent
    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                GreenDayAddon.GREEN_DAY_ENTITY.get(),
                StandEntity.createAttributes().build());
    }
}
