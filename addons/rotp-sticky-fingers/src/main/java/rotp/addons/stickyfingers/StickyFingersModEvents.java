package rotp.addons.stickyfingers;

import rotp.core.powersystem.standpower.entity.StandEntity;

import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

public final class StickyFingersModEvents {
    private StickyFingersModEvents() {}

    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                StickyFingersAddon.STICKY_FINGERS_ENTITY.get(),
                StandEntity.createAttributes().build());
    }
}
