package rotp.addons.aquanecklace;

import rotp.core.powersystem.standpower.entity.StandEntity;

import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

public final class AquaNecklaceEvents {
    private AquaNecklaceEvents() {}

    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                AquaNecklaceAddon.AQUA_NECKLACE_ENTITY.get(),
                StandEntity.createAttributes().build());
    }
}
