package rotp.addons.cmoonreimagined;

import rotp.core.powersystem.standpower.entity.StandEntity;

import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

public final class CMoonModEvents {
    private CMoonModEvents() {}

    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                CMoonAddon.C_MOON_ENTITY.get(),
                StandEntity.createAttributes().build());
    }
}
