package rotp.addons.stickyfingers.storage;

import java.util.UUID;

import javax.annotation.Nullable;

import rotp.addons.stickyfingers.StickyFingersPhase1Spec;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

public record EntityZipperStorageRecord(
        UUID targetId,
        UUID createdBy,
        long createdAt,
        ZipperStorageInventory inventory) {
    private static final String TARGET_ID_TAG = "TargetId";
    private static final String CREATED_BY_TAG = "CreatedBy";
    private static final String CREATED_AT_TAG = "CreatedAt";
    private static final String INVENTORY_TAG = "Inventory";

    public CompoundTag toNbt(
            HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(TARGET_ID_TAG, targetId);
        tag.putUUID(CREATED_BY_TAG, createdBy);
        tag.putLong(CREATED_AT_TAG, createdAt);
        tag.put(
                INVENTORY_TAG,
                inventory.save(registries));
        return tag;
    }

    @Nullable
    public static EntityZipperStorageRecord fromNbt(
            CompoundTag tag,
            HolderLookup.Provider registries,
            Runnable changed) {
        if (!tag.hasUUID(TARGET_ID_TAG)
                || !tag.hasUUID(CREATED_BY_TAG)) {
            return null;
        }
        ZipperStorageInventory inventory =
                ZipperStorageInventory.load(
                        tag.getCompound(INVENTORY_TAG),
                        StickyFingersPhase1Spec
                                .ENTITY_STORAGE_SLOTS,
                        registries,
                        changed);
        return new EntityZipperStorageRecord(
                tag.getUUID(TARGET_ID_TAG),
                tag.getUUID(CREATED_BY_TAG),
                tag.getLong(CREATED_AT_TAG),
                inventory);
    }
}
