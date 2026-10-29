package rotp.addons.mandom.entity;

import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class MandomStandEntity extends StandEntity {
    public MandomStandEntity(
            EntityType<? extends MandomStandEntity> entityType,
            Level level) {
        super(entityType, level);
        // 1.16 MandomEntity.offsetDefault (0, 0, 0); its y was also the y of
        // every task offset that set none.
        Y_OFFSET = 0.0D;
        offsetFromUser = new StandOffsetFromUser(
                this,
                new Vec3(0.0D, Y_OFFSET, 0.0D),
                StandOffsetFromUser.Rotations.HEAD);
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
