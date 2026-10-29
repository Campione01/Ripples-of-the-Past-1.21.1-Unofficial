package rotp.addons.frosttraveler.entity;

import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class CreamStarterStandEntity extends StandEntity {
    public CreamStarterStandEntity(
            EntityType<? extends CreamStarterStandEntity> type,
            Level level) {
        super(type, level);
        // 1.16 CSEntity.offsetDefault (0, 0, 0); its y was also the y of
        // every task offset that set none.
        Y_OFFSET = 0.0D;
        offsetFromUser = new StandOffsetFromUser(
                this,
                new Vec3(0.0D, Y_OFFSET, 0.0D),
                StandOffsetFromUser.Rotations.HEAD)
                .grabOffset(offsetFromUser.grabIdleOffset);
    }

    // 1.16 CSEntity.canBeCollidedWith: it stands inside its user, so it
    // must not take the hits aimed at the user.
    @Override
    public boolean isPickable() {
        return false;
    }
}
