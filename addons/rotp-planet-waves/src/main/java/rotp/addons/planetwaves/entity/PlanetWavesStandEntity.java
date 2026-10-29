package rotp.addons.planetwaves.entity;

import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public final class PlanetWavesStandEntity extends StandEntity {
    public PlanetWavesStandEntity(
            EntityType<? extends PlanetWavesStandEntity> type,
            Level level) {
        super(type, level);
    }
}
