package rotp.addons.pearljam.entity;

import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.pearljam.PearlJamAddon;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public final class PearlJamEntity extends StandEntity {
    public PearlJamEntity(
            EntityType<? extends PearlJamEntity> type,
            Level level) {
        super(type, level);
        withStandType(PearlJamAddon.PEARL_JAM.get());
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    public void tick() {
        super.tick();
        noPhysics = true;
        setNoGravity(true);
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
