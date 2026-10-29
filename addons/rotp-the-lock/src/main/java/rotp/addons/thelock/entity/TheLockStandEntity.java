package rotp.addons.thelock.entity;

import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public final class TheLockStandEntity extends StandEntity {
    public TheLockStandEntity(
            EntityType<? extends TheLockStandEntity> entityType,
            Level level) {
        super(entityType, level);
        noPhysics = true;
        setInvisible(true);
    }

    @Override
    public void tick() {
        super.tick();
        noPhysics = true;
        setInvisible(true);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return false;
    }
}
