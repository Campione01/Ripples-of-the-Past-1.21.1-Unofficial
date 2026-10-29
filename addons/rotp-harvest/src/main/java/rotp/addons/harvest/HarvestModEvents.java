package rotp.addons.harvest;

import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.harvest.entity.HarvestMemberEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@EventBusSubscriber(
        modid = HarvestAddon.MOD_ID,
        bus = EventBusSubscriber.Bus.MOD)
public final class HarvestModEvents {
    private HarvestModEvents() {}

    @SubscribeEvent
    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                HarvestAddon.HARVEST_STAND_ENTITY.get(),
                StandEntity.createAttributes().build());
        event.put(
                HarvestAddon.HARVEST_MEMBER.get(),
                HarvestMemberEntity.createAttributes().build());
    }
}
