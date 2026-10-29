package rotp.addons.stickyfingers.state;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public record ZipperLeaseRecord(
        UUID leaseId,
        UUID ownerId,
        ResourceLocation dimension,
        BlockPos position,
        long createdAt,
        long expiresAt,
        boolean open,
        CompoundTag originalState,
        @Nullable CompoundTag originalBlockEntity,
        @Nullable ZipperCloseReason closeReason,
        long closeRequestedAt,
        @Nullable BlockPos linkedPosition) {
    private static final String LEASE_ID_TAG = "LeaseId";
    private static final String OWNER_ID_TAG = "OwnerId";
    private static final String DIMENSION_TAG = "Dimension";
    private static final String POSITION_TAG = "Position";
    private static final String CREATED_AT_TAG = "CreatedAt";
    private static final String EXPIRES_AT_TAG = "ExpiresAt";
    private static final String OPEN_TAG = "Open";
    private static final String ORIGINAL_STATE_TAG = "OriginalState";
    private static final String ORIGINAL_BLOCK_ENTITY_TAG =
            "OriginalBlockEntity";
    private static final String CLOSE_REASON_TAG = "CloseReason";
    private static final String CLOSE_REQUESTED_AT_TAG =
            "CloseRequestedAt";
    // Absent in leases saved before zippers were linked.
    private static final String LINKED_POSITION_TAG = "LinkedPosition";

    public ZipperLeaseRecord {
        if (leaseId == null
                || ownerId == null
                || dimension == null
                || position == null
                || originalState == null
                || originalState.isEmpty()) {
            throw new IllegalArgumentException(
                    "A zipper lease requires complete identity and state.");
        }
        if (expiresAt < createdAt) {
            throw new IllegalArgumentException(
                    "A zipper lease cannot expire before it is created.");
        }
        if (closeReason == null && closeRequestedAt != 0L) {
            throw new IllegalArgumentException(
                    "Only a closing lease may have a close timestamp.");
        }
        originalState = originalState.copy();
        originalBlockEntity = originalBlockEntity != null
                ? originalBlockEntity.copy()
                : null;
        linkedPosition = linkedPosition != null
                ? linkedPosition.immutable()
                : null;
    }

    @Override
    public CompoundTag originalState() {
        return originalState.copy();
    }

    @Override
    @Nullable
    public CompoundTag originalBlockEntity() {
        return originalBlockEntity != null
                ? originalBlockEntity.copy()
                : null;
    }

    public boolean isPendingRestore() {
        return closeReason != null;
    }

    public ZipperLeaseRecord withOpen(boolean value) {
        return new ZipperLeaseRecord(
                leaseId,
                ownerId,
                dimension,
                position,
                createdAt,
                expiresAt,
                value,
                originalState,
                originalBlockEntity,
                closeReason,
                closeRequestedAt,
                linkedPosition);
    }

    public ZipperLeaseRecord requestClose(
            ZipperCloseReason reason, long gameTime) {
        if (isPendingRestore()) {
            return this;
        }
        return new ZipperLeaseRecord(
                leaseId,
                ownerId,
                dimension,
                position,
                createdAt,
                expiresAt,
                open,
                originalState,
                originalBlockEntity,
                reason,
                gameTime,
                linkedPosition);
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(LEASE_ID_TAG, leaseId);
        tag.putUUID(OWNER_ID_TAG, ownerId);
        tag.putString(DIMENSION_TAG, dimension.toString());
        tag.putLong(POSITION_TAG, position.asLong());
        tag.putLong(CREATED_AT_TAG, createdAt);
        tag.putLong(EXPIRES_AT_TAG, expiresAt);
        tag.putBoolean(OPEN_TAG, open);
        tag.put(ORIGINAL_STATE_TAG, originalState.copy());
        if (originalBlockEntity != null) {
            tag.put(
                    ORIGINAL_BLOCK_ENTITY_TAG,
                    originalBlockEntity.copy());
        }
        if (closeReason != null) {
            tag.putString(CLOSE_REASON_TAG, closeReason.name());
            tag.putLong(
                    CLOSE_REQUESTED_AT_TAG, closeRequestedAt);
        }
        if (linkedPosition != null) {
            tag.putLong(
                    LINKED_POSITION_TAG, linkedPosition.asLong());
        }
        return tag;
    }

    @Nullable
    public static ZipperLeaseRecord fromNbt(CompoundTag tag) {
        if (!tag.hasUUID(LEASE_ID_TAG)
                || !tag.hasUUID(OWNER_ID_TAG)
                || !tag.contains(DIMENSION_TAG)
                || !tag.contains(POSITION_TAG)
                || !tag.contains(CREATED_AT_TAG)
                || !tag.contains(EXPIRES_AT_TAG)
                || !tag.contains(ORIGINAL_STATE_TAG)) {
            return null;
        }
        try {
            ZipperCloseReason reason = tag.contains(CLOSE_REASON_TAG)
                    ? ZipperCloseReason.valueOf(
                            tag.getString(CLOSE_REASON_TAG))
                    : null;
            return new ZipperLeaseRecord(
                    tag.getUUID(LEASE_ID_TAG),
                    tag.getUUID(OWNER_ID_TAG),
                    ResourceLocation.parse(
                            tag.getString(DIMENSION_TAG)),
                    BlockPos.of(tag.getLong(POSITION_TAG)),
                    tag.getLong(CREATED_AT_TAG),
                    tag.getLong(EXPIRES_AT_TAG),
                    tag.getBoolean(OPEN_TAG),
                    tag.getCompound(ORIGINAL_STATE_TAG),
                    tag.contains(ORIGINAL_BLOCK_ENTITY_TAG)
                            ? tag.getCompound(
                                    ORIGINAL_BLOCK_ENTITY_TAG)
                            : null,
                    reason,
                    reason != null
                            ? tag.getLong(CLOSE_REQUESTED_AT_TAG)
                            : 0L,
                    tag.contains(LINKED_POSITION_TAG)
                            ? BlockPos.of(
                                    tag.getLong(LINKED_POSITION_TAG))
                            : null);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
