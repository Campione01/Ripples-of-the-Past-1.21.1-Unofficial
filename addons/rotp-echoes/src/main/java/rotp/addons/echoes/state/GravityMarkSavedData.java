package rotp.addons.echoes.state;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.saveddata.SavedData;

public final class GravityMarkSavedData extends SavedData {
    public static final int MAX_MARKS = 256;
    public static final int MARK_LIFETIME_TICKS = 600;

    private static final String FILE_NAME = "rotp_echoes-gravity_marks";
    private static final SavedData.Factory<GravityMarkSavedData> FACTORY =
            new SavedData.Factory<>(
                    GravityMarkSavedData::new,
                    GravityMarkSavedData::load);

    private final Map<Long, Mark> marksByPosition = new HashMap<>();
    private final Map<UUID, Long> positionByOwner = new HashMap<>();

    public static GravityMarkSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, FILE_NAME);
    }

    public static GravityMarkSavedData load(
            CompoundTag tag, HolderLookup.Provider registries) {
        GravityMarkSavedData data = new GravityMarkSavedData();
        for (Tag element : tag.getList("Marks", Tag.TAG_COMPOUND)) {
            if (!(element instanceof CompoundTag entry)
                    || !entry.hasUUID("Owner")
                    || data.marksByPosition.size() >= MAX_MARKS) {
                continue;
            }
            Mark mark = new Mark(
                    BlockPos.of(entry.getLong("Position")),
                    entry.getUUID("Owner"),
                    Math.max(0L, entry.getLong("ExpiresAt")));
            data.insert(mark);
        }
        return data;
    }

    @Override
    public CompoundTag save(
            CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        marksByPosition.values().stream()
                .sorted(Comparator.comparingLong(
                        mark -> mark.position().asLong()))
                .forEach(mark -> {
                    CompoundTag entry = new CompoundTag();
                    entry.putLong("Position", mark.position().asLong());
                    entry.putUUID("Owner", mark.owner());
                    entry.putLong("ExpiresAt", mark.expiresAt());
                    entries.add(entry);
                });
        tag.put("Marks", entries);
        return tag;
    }

    @Nullable
    public Mark mark(BlockPos position, long gameTime) {
        Mark mark = marksByPosition.get(position.asLong());
        if (mark != null && mark.expiresAt() <= gameTime) {
            remove(position);
            return null;
        }
        return mark;
    }

    public boolean mark(
            BlockPos position, UUID owner, long expiresAt) {
        removeOwner(owner);
        Mark displaced = marksByPosition.get(position.asLong());
        if (displaced != null) {
            remove(position);
        }
        if (marksByPosition.size() >= MAX_MARKS) {
            return false;
        }
        insert(new Mark(position.immutable(), owner, expiresAt));
        setDirty();
        return true;
    }

    public boolean remove(BlockPos position) {
        Mark removed = marksByPosition.remove(position.asLong());
        if (removed == null) {
            return false;
        }
        positionByOwner.remove(removed.owner(), position.asLong());
        setDirty();
        return true;
    }

    public boolean removeOwner(UUID owner) {
        Long position = positionByOwner.remove(owner);
        if (position == null) {
            return false;
        }
        marksByPosition.remove(position);
        setDirty();
        return true;
    }

    public void prune(long gameTime) {
        boolean changed = marksByPosition.values().removeIf(mark -> {
            if (mark.expiresAt() > gameTime) {
                return false;
            }
            positionByOwner.remove(mark.owner(), mark.position().asLong());
            return true;
        });
        if (changed) {
            setDirty();
        }
    }

    public int remainingSeconds(Mark mark, long gameTime) {
        return Mth.ceil(Math.max(0L, mark.expiresAt() - gameTime) / 20.0D);
    }

    public int size() {
        return marksByPosition.size();
    }

    private void insert(Mark mark) {
        marksByPosition.put(mark.position().asLong(), mark);
        Long previous = positionByOwner.put(
                mark.owner(), mark.position().asLong());
        if (previous != null && previous != mark.position().asLong()) {
            marksByPosition.remove(previous);
        }
    }

    public record Mark(
            BlockPos position, UUID owner, long expiresAt) {}
}
