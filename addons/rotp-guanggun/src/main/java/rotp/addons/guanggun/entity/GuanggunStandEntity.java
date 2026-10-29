package rotp.addons.guanggun.entity;

import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public final class GuanggunStandEntity extends StandEntity {
    public GuanggunStandEntity(
            EntityType<? extends GuanggunStandEntity> type,
            Level level) {
        super(type, level);
    }
}
