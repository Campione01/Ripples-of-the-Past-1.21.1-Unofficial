package rotp.addons.tusk.entity;

import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.powersystem.standpower.type.StandType;

import rotp.addons.tusk.stand.TuskStandType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class TuskEntity extends StandEntity {
    // 1.16 TuskEntity.getDefaultOffsetFromUser, from the user's feet.
    public static final Vec3 ACT_1_FOLLOW_OFFSET =
            new Vec3(-0.75D, 1.5D, -0.75D);
    public static final Vec3 ACT_2_FOLLOW_OFFSET =
            new Vec3(-0.75D, 0.75D, -0.75D);
    public static final Vec3 ACT_3_FOLLOW_OFFSET =
            new Vec3(-0.75D, 0.2D, -0.75D);
    public static final Vec3 ACT_4_FOLLOW_OFFSET =
            new Vec3(0.0D, 0.5D, -1.5D);

    public TuskEntity(
            EntityType<? extends TuskEntity> entityType, Level level) {
        super(entityType, level);
        setFollowOffset(ACT_1_FOLLOW_OFFSET);
    }

    @Override
    public TuskEntity withStandType(StandType standType) {
        super.withStandType(standType);
        applyActOffset(standType);
        return this;
    }

    // The client builds the Stand from the spawn data, not withStandType.
    @Override
    public void readSpawnData(RegistryFriendlyByteBuf additionalData) {
        super.readSpawnData(additionalData);
        applyActOffset(StandType.fromId(getStandType()));
    }

    private void applyActOffset(StandType standType) {
        setFollowOffset(standType instanceof TuskStandType tuskStand
                ? tuskStand.followOffset()
                : ACT_1_FOLLOW_OFFSET);
    }

    // 1.16 turned the act's offset with the view yaw, and its y was the
    // default y of task offsets without their own. Arms-only keeps the core
    // default, as 1.16 did; the grab offset is the core's.
    private void setFollowOffset(Vec3 offset) {
        Y_OFFSET = offset.y;
        offsetFromUser.idleOffset = offset;
        offsetFromUser.idleRotations = StandOffsetFromUser.Rotations.HEAD;
        offsetFromUser.grabIdleOffset = new Vec3(-1.0D, Y_OFFSET, 1.5D);
        offsetFromUser.resetToIdle();
    }
}
