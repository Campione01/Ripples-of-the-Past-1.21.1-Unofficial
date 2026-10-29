package rotp.addons.stickyfingers.state;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public record ZippedWoundRecord(
        UUID leaseId,
        UUID ownerId,
        UUID targetId,
        ResourceLocation dimension,
        long createdAt,
        long expiresAt) {
    private static final String LEASE_ID_TAG = "LeaseId";
    private static final String OWNER_ID_TAG = "OwnerId";
    private static final String TARGET_ID_TAG = "TargetId";
    private static final String DIMENSION_TAG = "Dimension";
    private static final String CREATED_AT_TAG = "CreatedAt";
    private static final String EXPIRES_AT_TAG = "ExpiresAt";

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(LEASE_ID_TAG, leaseId);
        tag.putUUID(OWNER_ID_TAG, ownerId);
        tag.putUUID(TARGET_ID_TAG, targetId);
        tag.putString(DIMENSION_TAG, dimension.toString());
        tag.putLong(CREATED_AT_TAG, createdAt);
        tag.putLong(EXPIRES_AT_TAG, expiresAt);
        return tag;
    }

    @Nullable
    public static ZippedWoundRecord fromNbt(CompoundTag tag) {
        if (!tag.hasUUID(LEASE_ID_TAG)
                || !tag.hasUUID(OWNER_ID_TAG)
                || !tag.hasUUID(TARGET_ID_TAG)) {
            return null;
        }
        ResourceLocation dimension = ResourceLocation.tryParse(
                tag.getString(DIMENSION_TAG));
        if (dimension == null) {
            return null;
        }
        long createdAt = tag.getLong(CREATED_AT_TAG);
        long expiresAt = tag.getLong(EXPIRES_AT_TAG);
        if (expiresAt <= createdAt) {
            return null;
        }
        return new ZippedWoundRecord(
                tag.getUUID(LEASE_ID_TAG),
                tag.getUUID(OWNER_ID_TAG),
                tag.getUUID(TARGET_ID_TAG),
                dimension,
                createdAt,
                expiresAt);
    }
}
