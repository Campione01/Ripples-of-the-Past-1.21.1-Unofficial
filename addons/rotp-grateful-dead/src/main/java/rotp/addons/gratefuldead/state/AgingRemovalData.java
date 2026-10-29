package rotp.addons.gratefuldead.state;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class AgingRemovalData extends SavedData {
    private static final String FILE_NAME =
            "rotp_grateful_dead-removal_generations";
    private static final String OWNERS_TAG = "Owners";
    private static final SavedData.Factory<AgingRemovalData>
            FACTORY = new SavedData.Factory<>(
                    AgingRemovalData::new,
                    AgingRemovalData::load);

    private final Map<UUID, Long> generations =
            new HashMap<>();

    public static AgingRemovalData get(
            MinecraftServer server) {
        return server.overworld()
                .getDataStorage()
                .computeIfAbsent(FACTORY, FILE_NAME);
    }

    public static AgingRemovalData load(
            CompoundTag tag,
            HolderLookup.Provider registries) {
        AgingRemovalData data = new AgingRemovalData();
        for (Tag element :
                tag.getList(OWNERS_TAG, Tag.TAG_COMPOUND)) {
            if (!(element instanceof CompoundTag ownerTag)
                    || !ownerTag.hasUUID("Owner")) {
                continue;
            }
            data.generations.put(
                    ownerTag.getUUID("Owner"),
                    Math.max(
                            0L,
                            ownerTag.getLong("Generation")));
        }
        return data;
    }

    public long generation(UUID owner) {
        return generations.getOrDefault(owner, 0L);
    }

    public long advance(UUID owner) {
        long next = generation(owner) + 1L;
        generations.put(owner, next);
        setDirty();
        return next;
    }

    @Override
    public CompoundTag save(
            CompoundTag tag,
            HolderLookup.Provider registries) {
        ListTag owners = new ListTag();
        generations.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    CompoundTag ownerTag = new CompoundTag();
                    ownerTag.putUUID("Owner", entry.getKey());
                    ownerTag.putLong(
                            "Generation", entry.getValue());
                    owners.add(ownerTag);
                });
        tag.put(OWNERS_TAG, owners);
        return tag;
    }
}
