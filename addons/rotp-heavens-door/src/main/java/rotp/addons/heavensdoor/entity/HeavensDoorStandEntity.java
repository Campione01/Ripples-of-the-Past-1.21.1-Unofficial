package rotp.addons.heavensdoor.entity;

import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class HeavensDoorStandEntity extends StandEntity {
    // 1.16 HeavensDoorEntity.getDefaultOffsetFromUser: behind the user's
    // right shoulder, raised, and arms-only raised too. Its y of 0.75 is the
    // default y that 1.16 gave task offsets without their own.
    public HeavensDoorStandEntity(
            EntityType<? extends HeavensDoorStandEntity> type,
            Level level) {
        super(type, level);
        Y_OFFSET = 0.75D;
        offsetFromUser.idleOffset = new Vec3(-0.5D, Y_OFFSET, -0.65D);
        offsetFromUser.idleRotations = StandOffsetFromUser.Rotations.HEAD;
        offsetFromUser.armsOnlyIdleOffset = new Vec3(0.0D, 0.35D, 0.15D);
        offsetFromUser.armsOnlyIdleRotations =
                StandOffsetFromUser.Rotations.HEAD;
        offsetFromUser.grabIdleOffset = new Vec3(-1.0D, Y_OFFSET, 1.5D);
        offsetFromUser.resetToIdle();
    }
}
