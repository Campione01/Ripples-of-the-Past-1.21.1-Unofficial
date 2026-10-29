package rotp.addons.tickettoride.state;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class TearsWoundState
        implements INBTSerializable<CompoundTag> {
    public static final int SERIALIZATION_VERSION = 1;

    private static final String VERSION_TAG = "Version";
    private static final String ACTIVE_TAG = "Active";
    private static final String OWNER_TAG = "Owner";
    private static final String EXPIRES_AT_TAG = "ExpiresAt";

    private boolean active;
    @Nullable
    private UUID ownerUuid;
    private long expiresAt;

    public boolean active() {
        return active && ownerUuid != null;
    }

    @Nullable
    public UUID ownerUuid() {
        return ownerUuid;
    }

    public long expiresAt() {
        return expiresAt;
    }

    public boolean isOwnedBy(UUID owner) {
        return owner != null
                && owner.equals(ownerUuid)
                && active();
    }

    public boolean mark(UUID owner, long newExpiresAt) {
        if (owner == null) {
            return clear();
        }
        boolean changed = !active()
                || !owner.equals(ownerUuid)
                || expiresAt != newExpiresAt;
        active = true;
        ownerUuid = owner;
        expiresAt = Math.max(0L, newExpiresAt);
        return changed;
    }

    public boolean clear() {
        boolean changed =
                active || ownerUuid != null || expiresAt != 0L;
        active = false;
        ownerUuid = null;
        expiresAt = 0L;
        return changed;
    }

    public void applyClientSnapshot(
            boolean newActive,
            @Nullable UUID newOwner,
            long newExpiresAt) {
        if (!newActive || newOwner == null) {
            clear();
            return;
        }
        active = true;
        ownerUuid = newOwner;
        expiresAt = Math.max(0L, newExpiresAt);
    }

    @Override
    public CompoundTag serializeNBT(
            HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        if (!active()) {
            return tag;
        }
        tag.putInt(VERSION_TAG, SERIALIZATION_VERSION);
        tag.putBoolean(ACTIVE_TAG, true);
        tag.putUUID(OWNER_TAG, ownerUuid);
        tag.putLong(EXPIRES_AT_TAG, expiresAt);
        return tag;
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider, CompoundTag tag) {
        clear();
        if (tag.getInt(VERSION_TAG) != SERIALIZATION_VERSION
                || !tag.getBoolean(ACTIVE_TAG)
                || !tag.hasUUID(OWNER_TAG)) {
            return;
        }
        active = true;
        ownerUuid = tag.getUUID(OWNER_TAG);
        expiresAt = Math.max(
                0L, tag.getLong(EXPIRES_AT_TAG));
    }
}
