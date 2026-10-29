package rotp.addons.spicegirl;

import rotp.core.powersystem.standpower.entity.StandEntity;

import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

public final class SpiceGirlModEvents {
    private SpiceGirlModEvents() {}

    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                SpiceGirlAddon.SPICE_GIRL_ENTITY.get(),
                StandEntity.createAttributes().build());
    }
}
