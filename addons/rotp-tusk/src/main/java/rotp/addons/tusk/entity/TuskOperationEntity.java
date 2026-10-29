package rotp.addons.tusk.entity;

import java.util.Optional;
import java.util.UUID;

import rotp.addons.tusk.state.TuskPortalOperations;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public abstract class TuskOperationEntity extends Entity {
    private static final String OPERATION_TAG = "Operation";
    private static final String OWNER_TAG = "Owner";
    private static final String EXPIRES_AT_TAG = "ExpiresAt";

    private static final EntityDataAccessor<Optional<UUID>>
            OPERATION_UUID = SynchedEntityData.defineId(
                    TuskOperationEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID =
            SynchedEntityData.defineId(
                    TuskOperationEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Long> EXPIRES_AT =
            SynchedEntityData.defineId(
                    TuskOperationEntity.class,
                    EntityDataSerializers.LONG);

    public static final double SINK_PER_TICK = 0.7D;

    private long ownedMoveGameTime = Long.MIN_VALUE / 2L;

    protected TuskOperationEntity(
            EntityType<? extends TuskOperationEntity> entityType,
            Level level) {
        super(entityType, level);
    }

    public final UUID getOperationUuid() {
        return entityData.get(OPERATION_UUID).orElse(null);
    }

    public final UUID getOwnerUuid() {
        return entityData.get(OWNER_UUID).orElse(null);
    }

    public final long getExpiresAt() {
        return entityData.get(EXPIRES_AT);
    }

    public final void initializeOperation(
            UUID operationUuid, UUID ownerUuid, long expiresAt) {
        entityData.set(
                OPERATION_UUID, Optional.ofNullable(operationUuid));
        entityData.set(OWNER_UUID, Optional.ofNullable(ownerUuid));
        entityData.set(EXPIRES_AT, expiresAt);
    }

    public abstract TuskPortalOperations.MemberRole memberRole();

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        UUID operationUuid = getOperationUuid();
        if (operationUuid == null) {
            discard();
            return;
        }
        TuskPortalOperations operations =
                TuskPortalOperations.get(serverLevel.getServer());
        if (serverLevel.getGameTime() >= getExpiresAt()) {
            operations.closeOperation(operationUuid);
            return;
        }
        if (!operations.validateMember(this)) {
            discard();
            return;
        }
        // An owned move is this tick's move; the tick after it is skipped whichever of the two ran first.
        if (serverLevel.getGameTime() - ownedMoveGameTime > 1L) {
            Vec3 before = position();
            sinkMove();
            if (position().distanceToSqr(before) > 1.0E-12D) {
                operations.updateMemberPosition(this);
            }
        }
    }

    // 1.16 AbstractWormholeEntity.tick: a 0.7 step up after a vertical collision, then the motion plus a 0.7 sink.
    private void sinkMove() {
        if (verticalCollision) {
            move(
                    MoverType.SELF,
                    getDeltaMovement().add(0.0D, SINK_PER_TICK, 0.0D));
        }
        move(
                MoverType.SELF,
                getDeltaMovement().add(0.0D, -SINK_PER_TICK, 0.0D));
    }

    public final void ownedMove(long gameTime) {
        ownedMoveGameTime = gameTime;
        sinkMove();
    }

    @Override
    public void remove(RemovalReason reason) {
        boolean wasRemoved = isRemoved();
        super.remove(reason);
        if (!wasRemoved
                && reason.shouldDestroy()
                && level() instanceof ServerLevel serverLevel) {
            UUID operationUuid = getOperationUuid();
            if (operationUuid != null) {
                TuskPortalOperations.get(serverLevel.getServer())
                        .closeOperation(operationUuid);
            }
        }
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        builder.define(OPERATION_UUID, Optional.empty());
        builder.define(OWNER_UUID, Optional.empty());
        builder.define(EXPIRES_AT, 0L);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        UUID operationUuid = getOperationUuid();
        if (operationUuid != null) {
            tag.putUUID(OPERATION_TAG, operationUuid);
        }
        UUID ownerUuid = getOwnerUuid();
        if (ownerUuid != null) {
            tag.putUUID(OWNER_TAG, ownerUuid);
        }
        tag.putLong(EXPIRES_AT_TAG, getExpiresAt());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        initializeOperation(
                tag.hasUUID(OPERATION_TAG)
                        ? tag.getUUID(OPERATION_TAG)
                        : null,
                tag.hasUUID(OWNER_TAG) ? tag.getUUID(OWNER_TAG) : null,
                tag.getLong(EXPIRES_AT_TAG));
    }
}
