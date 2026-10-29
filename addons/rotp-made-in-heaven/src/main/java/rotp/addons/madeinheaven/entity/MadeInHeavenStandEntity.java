package rotp.addons.madeinheaven.entity;

import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public final class MadeInHeavenStandEntity extends StandEntity {
    public MadeInHeavenStandEntity(
            EntityType<? extends StandEntity> entityType,
            Level level) {
        super(entityType, level);
    }
}
