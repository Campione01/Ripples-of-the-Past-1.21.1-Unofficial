package rotp.addons.guanggun;

import rotp.core.powersystem.standpower.entity.StandEntity;

import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

public final class GuanggunEvents {
    private GuanggunEvents() {}

    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                GuanggunAddon.GUANGGUN_ENTITY.get(),
                StandEntity.createAttributes().build());
    }
}
