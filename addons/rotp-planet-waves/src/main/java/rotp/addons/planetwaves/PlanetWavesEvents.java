package rotp.addons.planetwaves;

import rotp.core.powersystem.standpower.entity.StandEntity;

import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

public final class PlanetWavesEvents {
    private PlanetWavesEvents() {}

    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                PlanetWavesAddon.PLANET_WAVES_ENTITY.get(),
                StandEntity.createAttributes().build());
    }
}
