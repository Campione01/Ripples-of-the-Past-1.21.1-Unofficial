package rotp.addons.gratefuldead.entity;

import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public final class GratefulDeadStandEntity extends StandEntity {
    public GratefulDeadStandEntity(
            EntityType<? extends GratefulDeadStandEntity> type,
            Level level) {
        super(type, level);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }
}
