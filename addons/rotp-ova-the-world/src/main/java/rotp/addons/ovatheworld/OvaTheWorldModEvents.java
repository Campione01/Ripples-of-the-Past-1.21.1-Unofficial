package rotp.addons.ovatheworld;

import rotp.core.powersystem.standpower.entity.StandEntity;

import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

public final class OvaTheWorldModEvents {
    private OvaTheWorldModEvents() {}

    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                OvaTheWorldAddon.THE_WORLD_OVA_ENTITY.get(),
                StandEntity.createAttributes().build());
    }
}
