package rotp.addons.tickettoride.state;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class LoveTrainState
        implements INBTSerializable<CompoundTag> {
    public static final int SERIALIZATION_VERSION = 1;

    private long startedAt;
    private long expiresAt;

    public boolean active(long gameTime) {
        return startedAt > 0L && gameTime <= expiresAt;
    }

    public long startedAt() {
        return startedAt;
    }

    public long expiresAt() {
        return expiresAt;
    }

    public void activate(long start, long expiry) {
        startedAt = Math.max(1L, start);
        expiresAt = Math.max(startedAt, expiry);
    }

    public boolean clear() {
        boolean changed = startedAt != 0L || expiresAt != 0L;
        startedAt = 0L;
        expiresAt = 0L;
        return changed;
    }

    @Override
    public CompoundTag serializeNBT(
            HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        if (startedAt == 0L) {
            return tag;
        }
        tag.putInt("Version", SERIALIZATION_VERSION);
        tag.putLong("StartedAt", startedAt);
        tag.putLong("ExpiresAt", expiresAt);
        return tag;
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider, CompoundTag tag) {
        clear();
        if (tag.getInt("Version") != SERIALIZATION_VERSION) {
            return;
        }
        startedAt = Math.max(0L, tag.getLong("StartedAt"));
        expiresAt = Math.max(
                startedAt, tag.getLong("ExpiresAt"));
    }
}
