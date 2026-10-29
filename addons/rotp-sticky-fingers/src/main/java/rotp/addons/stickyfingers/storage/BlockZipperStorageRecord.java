package rotp.addons.stickyfingers.storage;

import java.util.UUID;

import javax.annotation.Nullable;

import rotp.addons.stickyfingers.StickyFingersPhase1Spec;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

public record BlockZipperStorageRecord(
        BlockZipperStorageKey key,
        UUID ownerId,
        long createdAt,
        ZipperStorageInventory inventory) {
    private static final String KEY_TAG = "Key";
    private static final String OWNER_ID_TAG = "OwnerId";
    private static final String CREATED_AT_TAG = "CreatedAt";
    private static final String INVENTORY_TAG = "Inventory";

    public CompoundTag toNbt(
            HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put(KEY_TAG, key.toNbt());
        tag.putUUID(OWNER_ID_TAG, ownerId);
        tag.putLong(CREATED_AT_TAG, createdAt);
        tag.put(
                INVENTORY_TAG,
                inventory.save(registries));
        return tag;
    }

    @Nullable
    public static BlockZipperStorageRecord fromNbt(
            CompoundTag tag,
            HolderLookup.Provider registries,
            Runnable changed) {
        if (!tag.hasUUID(OWNER_ID_TAG)) {
            return null;
        }
        BlockZipperStorageKey key =
                BlockZipperStorageKey.fromNbt(
                        tag.getCompound(KEY_TAG));
        if (key == null) {
            return null;
        }
        ZipperStorageInventory inventory =
                ZipperStorageInventory.load(
                        tag.getCompound(INVENTORY_TAG),
                        StickyFingersPhase1Spec
                                .BLOCK_STORAGE_SLOTS,
                        registries,
                        changed);
        return new BlockZipperStorageRecord(
                key,
                tag.getUUID(OWNER_ID_TAG),
                tag.getLong(CREATED_AT_TAG),
                inventory);
    }
}
