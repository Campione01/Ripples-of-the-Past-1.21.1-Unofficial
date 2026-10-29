package rotp.addons.thesun;

import rotp.core.powersystem.standpower.entity.StandEntity;

import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

public final class TheSunModEvents {
    private TheSunModEvents() {}

    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                TheSunAddon.THE_SUN_ENTITY.get(),
                StandEntity.createAttributes().build());
    }
}
