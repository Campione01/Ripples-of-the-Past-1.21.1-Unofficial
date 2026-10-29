package rotp.addons.stickyfingers.blockentity;

import java.util.UUID;

import javax.annotation.Nullable;

import rotp.addons.stickyfingers.StickyFingersAddon;
import rotp.addons.stickyfingers.block.StickyFingersZipperBlock;
import rotp.addons.stickyfingers.state.ZipperCloseReason;
import rotp.addons.stickyfingers.state.ZipperLeaseData;
import rotp.addons.stickyfingers.state.ZipperLeaseRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class StickyFingersZipperBlockEntity
        extends BlockEntity {
    private static final String LEASE_ID_TAG = "LeaseId";
    private static final String OWNER_ID_TAG = "OwnerId";
    private static final String DIMENSION_TAG = "Dimension";
    private static final String CREATED_AT_TAG = "CreatedAt";
    private static final String EXPIRES_AT_TAG = "ExpiresAt";
    private static final String ORIGINAL_STATE_TAG = "OriginalState";
    private static final String ORIGINAL_BLOCK_ENTITY_TAG =
            "OriginalBlockEntity";

    @Nullable
    private UUID leaseId;
    @Nullable
    private UUID ownerId;
    @Nullable
    private ResourceLocation dimension;
    private long createdAt;
    private long expiresAt;
    @Nullable
    private CompoundTag originalState;
    @Nullable
    private CompoundTag originalBlockEntity;
    private int reconciliationDelay;

    public StickyFingersZipperBlockEntity(
            BlockPos position, BlockState state) {
        super(
                StickyFingersAddon.STICKY_FINGERS_ZIPPER_BLOCK_ENTITY
                        .get(),
                position,
                state);
    }

    public void initialize(ZipperLeaseRecord record) {
        leaseId = record.leaseId();
        ownerId = record.ownerId();
        dimension = record.dimension();
        createdAt = record.createdAt();
        expiresAt = record.expiresAt();
        originalState = record.originalState();
        originalBlockEntity = record.originalBlockEntity();
        reconciliationDelay = 20;
        setChanged();
    }

    @Nullable
    public UUID getLeaseId() {
        return leaseId;
    }

    public boolean matches(ZipperLeaseRecord record) {
        return leaseId != null
                && leaseId.equals(record.leaseId())
                && ownerId != null
                && ownerId.equals(record.ownerId())
                && dimension != null
                && dimension.equals(record.dimension())
                && worldPosition.equals(record.position())
                && createdAt == record.createdAt()
                && expiresAt == record.expiresAt()
                && originalState != null
                && originalState.equals(record.originalState())
                && equalNullable(
                        originalBlockEntity,
                        record.originalBlockEntity());
    }

    @Nullable
    public ZipperLeaseRecord toRecoveryRecord(
            ResourceLocation currentDimension, long gameTime) {
        if (leaseId == null
                || ownerId == null
                || dimension == null
                || !dimension.equals(currentDimension)
                || originalState == null
                || originalState.isEmpty()
                || expiresAt < createdAt) {
            return null;
        }
        boolean open = getBlockState().hasProperty(
                        StickyFingersZipperBlock.OPEN)
                && getBlockState().getValue(
                        StickyFingersZipperBlock.OPEN);
        return new ZipperLeaseRecord(
                leaseId,
                ownerId,
                dimension,
                worldPosition.immutable(),
                createdAt,
                expiresAt,
                open,
                originalState,
                originalBlockEntity,
                ZipperCloseReason.ORPHAN_RECOVERY,
                gameTime,
                null);
    }

    public static void serverTick(
            Level level,
            BlockPos position,
            BlockState state,
            StickyFingersZipperBlockEntity blockEntity) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (blockEntity.reconciliationDelay > 0) {
            blockEntity.reconciliationDelay--;
            return;
        }
        blockEntity.reconciliationDelay = 20;
        ZipperLeaseData.get(serverLevel)
                .reconcileBlockEntity(serverLevel, blockEntity);
    }

    @Override
    protected void saveAdditional(
            CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (leaseId != null) {
            tag.putUUID(LEASE_ID_TAG, leaseId);
        }
        if (ownerId != null) {
            tag.putUUID(OWNER_ID_TAG, ownerId);
        }
        if (dimension != null) {
            tag.putString(DIMENSION_TAG, dimension.toString());
        }
        tag.putLong(CREATED_AT_TAG, createdAt);
        tag.putLong(EXPIRES_AT_TAG, expiresAt);
        if (originalState != null) {
            tag.put(ORIGINAL_STATE_TAG, originalState.copy());
        }
        if (originalBlockEntity != null) {
            tag.put(
                    ORIGINAL_BLOCK_ENTITY_TAG,
                    originalBlockEntity.copy());
        }
    }

    @Override
    protected void loadAdditional(
            CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        leaseId = tag.hasUUID(LEASE_ID_TAG)
                ? tag.getUUID(LEASE_ID_TAG)
                : null;
        ownerId = tag.hasUUID(OWNER_ID_TAG)
                ? tag.getUUID(OWNER_ID_TAG)
                : null;
        dimension = tag.contains(DIMENSION_TAG)
                ? ResourceLocation.tryParse(
                        tag.getString(DIMENSION_TAG))
                : null;
        createdAt = tag.getLong(CREATED_AT_TAG);
        expiresAt = tag.getLong(EXPIRES_AT_TAG);
        originalState = tag.contains(ORIGINAL_STATE_TAG)
                ? tag.getCompound(ORIGINAL_STATE_TAG).copy()
                : null;
        originalBlockEntity =
                tag.contains(ORIGINAL_BLOCK_ENTITY_TAG)
                        ? tag.getCompound(
                                        ORIGINAL_BLOCK_ENTITY_TAG)
                                .copy()
                        : null;
        reconciliationDelay = 0;
    }

    private static boolean equalNullable(
            @Nullable CompoundTag first,
            @Nullable CompoundTag second) {
        return first == null ? second == null : first.equals(second);
    }
}
