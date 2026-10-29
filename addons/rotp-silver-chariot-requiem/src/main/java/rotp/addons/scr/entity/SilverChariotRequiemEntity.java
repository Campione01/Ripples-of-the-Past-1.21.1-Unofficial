package rotp.addons.scr.entity;

import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.scr.service.RequiemSoulService;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public final class SilverChariotRequiemEntity
        extends StandEntity {
    public SilverChariotRequiemEntity(
            EntityType<? extends SilverChariotRequiemEntity> type,
            Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide()) {
            RequiemSoulService.tickStand(this);
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }
}
