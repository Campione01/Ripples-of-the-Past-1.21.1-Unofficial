package rotp.addons.metallica.entity;

import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public final class MetallicaStandEntity extends StandEntity {
    public MetallicaStandEntity(
            EntityType<? extends MetallicaStandEntity> entityType,
            Level level) {
        super(entityType, level);
        // 1.16 Metallica kept StandEntity.offsetDefault, the core default.
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
