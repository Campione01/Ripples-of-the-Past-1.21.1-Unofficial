package rotp.addons.spicegirl.entity;

import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public final class SpiceGirlStandEntity extends StandEntity {
    public SpiceGirlStandEntity(
            EntityType<? extends SpiceGirlStandEntity> type,
            Level level) {
        super(type, level);
    }
}
