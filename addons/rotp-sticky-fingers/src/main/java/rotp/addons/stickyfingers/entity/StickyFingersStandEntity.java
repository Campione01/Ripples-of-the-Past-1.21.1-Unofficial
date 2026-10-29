package rotp.addons.stickyfingers.entity;

import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public final class StickyFingersStandEntity extends StandEntity {
    public StickyFingersStandEntity(
            EntityType<? extends StickyFingersStandEntity> type,
            Level level) {
        super(type, level);
    }
}
