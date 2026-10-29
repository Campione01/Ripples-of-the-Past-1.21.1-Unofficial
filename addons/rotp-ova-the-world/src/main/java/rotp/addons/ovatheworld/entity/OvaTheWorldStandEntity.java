package rotp.addons.ovatheworld.entity;

import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class OvaTheWorldStandEntity extends StandEntity {
    private static final Vec3 LEGACY_ARMS_ONLY_OFFSET =
            new Vec3(0.0D, 0.0D, 0.15D);

    // 1.16 TheWorldOvaEntity.getDefaultOffsetFromUser: 1 behind the user,
    // 0.6 up, turned with the view yaw. The 0.6 is also the default y of
    // task offsets without their own.
    public OvaTheWorldStandEntity(
            EntityType<? extends OvaTheWorldStandEntity> type,
            Level level) {
        super(type, level);
        Y_OFFSET = 0.6D;
        offsetFromUser = new StandOffsetFromUser(
                this,
                new Vec3(0.0D, Y_OFFSET, -1.0D),
                StandOffsetFromUser.Rotations.HEAD)
                .armsOnlyIdleOffset(
                        LEGACY_ARMS_ONLY_OFFSET,
                        StandOffsetFromUser.Rotations.HEAD)
                .grabOffset(new Vec3(-1.0D, Y_OFFSET, 1.5D));
    }
}
