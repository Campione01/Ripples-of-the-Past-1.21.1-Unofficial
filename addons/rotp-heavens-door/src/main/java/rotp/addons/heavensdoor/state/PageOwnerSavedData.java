package rotp.addons.heavensdoor.state;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Records revocation even when an affected target is offline or unloaded. */
public final class PageOwnerSavedData extends SavedData {
    private static final String DATA_NAME = "rotp_hd_page_owners";
    private static final Factory<PageOwnerSavedData> FACTORY =
            new Factory<>(PageOwnerSavedData::new, PageOwnerSavedData::load, null);
    private final Map<UUID, OwnerEpoch> owners = new HashMap<>();

    public static PageOwnerSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public boolean tracks(UUID owner) {
        return owners.containsKey(owner);
    }

    public long begin(UUID owner) {
        OwnerEpoch current = owners.get(owner);
        if (current == null || current.revoked()) {
            current = new OwnerEpoch(current != null ? current.epoch() : 0L, false);
            owners.put(owner, current);
            setDirty();
        }
        return current.epoch();
    }

    public boolean matches(UUID owner, long epoch) {
        OwnerEpoch current = owners.get(owner);
        return epoch >= 0L && epoch == (current != null ? current.epoch() : 0L)
                && (current == null || !current.revoked());
    }

    public boolean invalidate(UUID owner) {
        OwnerEpoch current = owners.get(owner);
        if (current != null && current.revoked()) {
            return false;
        }
        long epoch = current != null ? current.epoch() : 0L;
        owners.put(owner, new OwnerEpoch(Math.incrementExact(epoch), true));
        setDirty();
        return true;
    }

    public static boolean shouldInvalidate(
            boolean alive, float health, float maxHealth, boolean creative) {
        return !alive;
    }

    public static PageOwnerSavedData load(
            CompoundTag tag, HolderLookup.Provider provider) {
        PageOwnerSavedData data = new PageOwnerSavedData();
        ListTag entries = tag.getList("Owners", Tag.TAG_COMPOUND);
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag entry = entries.getCompound(index);
            if (entry.hasUUID("Owner")) {
                data.owners.put(entry.getUUID("Owner"), new OwnerEpoch(
                        Math.max(0L, entry.getLong("Epoch")), entry.getBoolean("Revoked")));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag entries = new ListTag();
        owners.forEach((owner, epoch) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Owner", owner);
            entry.putLong("Epoch", epoch.epoch());
            entry.putBoolean("Revoked", epoch.revoked());
            entries.add(entry);
        });
        tag.put("Owners", entries);
        return tag;
    }

    private record OwnerEpoch(long epoch, boolean revoked) {}
}
