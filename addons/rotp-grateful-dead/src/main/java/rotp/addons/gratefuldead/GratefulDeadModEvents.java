package rotp.addons.gratefuldead;

import rotp.core.powersystem.standpower.entity.StandEntity;

import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

public final class GratefulDeadModEvents {
    private GratefulDeadModEvents() {}

    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                GratefulDeadAddon.THE_GRATEFUL_DEAD_ENTITY.get(),
                StandEntity.createAttributes().build());
    }
}
