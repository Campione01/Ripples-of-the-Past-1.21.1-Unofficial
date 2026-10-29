package rotp.addons.tickettoride.entity;

import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public final class TicketToRideStandEntity extends StandEntity {
    public TicketToRideStandEntity(
            EntityType<? extends TicketToRideStandEntity> entityType,
            Level level) {
        super(entityType, level);
        noPhysics = true;
    }

    @Override
    public void tick() {
        super.tick();
        noPhysics = true;
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
