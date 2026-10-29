package rotp.addons.sexpistols;

import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.sexpistols.registry.SexPistolsEntities;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@EventBusSubscriber(
        modid = SexPistolsIds.MOD_ID,
        bus = EventBusSubscriber.Bus.MOD)
public final class SexPistolsModEvents {
    private SexPistolsModEvents() {}

    @SubscribeEvent
    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        AttributeSupplier attributes =
                StandEntity.createAttributes().build();
        SexPistolsEntities.members().forEach(member ->
                event.put(member.get(), attributes));
    }
}
