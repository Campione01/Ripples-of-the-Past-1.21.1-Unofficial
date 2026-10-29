package rotp.addons.spicegirl.state;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

public final class SoftAreaIndexState {
    public static final int MAX_ENTRIES = 512;
    public static final int MAX_MATCHES_PER_POSITION = 8;

    private final List<Entry> entries =
            new ArrayList<>(64);

    public boolean upsert(
            BlockPos position,
            UUID ownerUuid,
            UUID standUuid,
            long expiresAt,
            long gameTime) {
        if (position == null
                || ownerUuid == null
                || standUuid == null
                || expiresAt <= gameTime) {
            return false;
        }
        pruneExpired(gameTime);
        long packedPosition = position.asLong();
        Entry replacement = new Entry(
                packedPosition,
                ownerUuid,
                standUuid,
                expiresAt);
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (entry.blockPosition() == packedPosition
                    && entry.ownerUuid().equals(ownerUuid)) {
                entries.set(i, replacement);
                return true;
            }
        }
        if (entries.size() >= MAX_ENTRIES) {
            return false;
        }
        entries.add(replacement);
        return true;
    }

    public List<Entry> entriesAt(
            BlockPos position, long gameTime) {
        pruneExpired(gameTime);
        List<Entry> matches =
                new ArrayList<>(MAX_MATCHES_PER_POSITION);
        long packedPosition = position.asLong();
        for (Entry entry : entries) {
            if (entry.blockPosition() == packedPosition) {
                matches.add(entry);
                if (matches.size()
                        >= MAX_MATCHES_PER_POSITION) {
                    break;
                }
            }
        }
        return matches;
    }

    public boolean remove(Entry entry) {
        return entry != null && entries.remove(entry);
    }

    public int removeOwner(UUID ownerUuid) {
        int before = entries.size();
        entries.removeIf(entry ->
                entry.ownerUuid().equals(ownerUuid));
        return before - entries.size();
    }

    public int removePosition(
            BlockPos position, UUID ownerUuid) {
        if (position == null || ownerUuid == null) {
            return 0;
        }
        long packedPosition = position.asLong();
        int before = entries.size();
        entries.removeIf(entry ->
                entry.blockPosition() == packedPosition
                        && entry.ownerUuid()
                                .equals(ownerUuid));
        return before - entries.size();
    }

    public int removePosition(BlockPos position) {
        if (position == null) {
            return 0;
        }
        long packedPosition = position.asLong();
        int before = entries.size();
        entries.removeIf(entry ->
                entry.blockPosition() == packedPosition);
        return before - entries.size();
    }

    public void pruneExpired(long gameTime) {
        entries.removeIf(entry ->
                entry.expiresAt() <= gameTime);
    }

    public int size() {
        return entries.size();
    }

    public record Entry(
            long blockPosition,
            UUID ownerUuid,
            UUID standUuid,
            long expiresAt) {}

    // The level retains these bounded indexes across chunk and player removal.
    public static final class World {
        private final Map<Long, SoftAreaIndexState> chunkIndexes =
                new HashMap<>();

        public boolean upsert(
                BlockPos position,
                UUID ownerUuid,
                UUID standUuid,
                long expiresAt,
                long gameTime) {
            if (position == null
                    || ownerUuid == null
                    || standUuid == null
                    || expiresAt <= gameTime) {
                return false;
            }
            return chunkIndexes.computeIfAbsent(
                            chunkKey(position),
                            key -> new SoftAreaIndexState())
                    .upsert(
                            position,
                            ownerUuid,
                            standUuid,
                            expiresAt,
                            gameTime);
        }

        public boolean upsertArea(
                List<BlockPos> positions,
                UUID ownerUuid,
                UUID standUuid,
                long expiresAt,
                long gameTime) {
            if (positions == null
                    || positions.isEmpty()
                    || positions.size() > SoftAreaState.MAX_POSITIONS
                    || ownerUuid == null
                    || standUuid == null
                    || expiresAt <= gameTime) {
                return false;
            }

            // Stage only touched buckets; rejection leaves every prior lease intact.
            Map<Long, SoftAreaIndexState> stagedIndexes =
                    new HashMap<>();
            for (BlockPos position : positions) {
                if (position == null) {
                    return false;
                }
                long key = chunkKey(position);
                SoftAreaIndexState staged = stagedIndexes.get(key);
                if (staged == null) {
                    staged = new SoftAreaIndexState();
                    SoftAreaIndexState existing = chunkIndexes.get(key);
                    if (existing != null) {
                        for (Entry entry : existing.entries) {
                            if (entry.expiresAt() > gameTime) {
                                staged.entries.add(entry);
                            }
                        }
                    }
                    stagedIndexes.put(key, staged);
                }
                if (!staged.upsert(
                        position, ownerUuid, standUuid, expiresAt, gameTime)) {
                    return false;
                }
            }
            chunkIndexes.putAll(stagedIndexes);
            return true;
        }

        public List<Entry> entriesAt(
                BlockPos position, long gameTime) {
            if (position == null) {
                return List.of();
            }
            long key = chunkKey(position);
            SoftAreaIndexState index = chunkIndexes.get(key);
            if (index == null) {
                return List.of();
            }
            List<Entry> matches = index.entriesAt(position, gameTime);
            if (index.size() == 0) {
                chunkIndexes.remove(key);
            }
            return matches;
        }

        public int removeOwner(UUID ownerUuid) {
            if (ownerUuid == null) {
                return 0;
            }
            int removed = 0;
            Iterator<SoftAreaIndexState> indexes =
                    chunkIndexes.values().iterator();
            while (indexes.hasNext()) {
                SoftAreaIndexState index = indexes.next();
                removed += index.removeOwner(ownerUuid);
                if (index.size() == 0) {
                    indexes.remove();
                }
            }
            return removed;
        }

        public int removePosition(BlockPos position) {
            if (position == null) {
                return 0;
            }
            long key = chunkKey(position);
            SoftAreaIndexState index = chunkIndexes.get(key);
            if (index == null) {
                return 0;
            }
            int removed = index.removePosition(position);
            if (index.size() == 0) {
                chunkIndexes.remove(key);
            }
            return removed;
        }

        public void pruneExpired(long gameTime) {
            Iterator<SoftAreaIndexState> indexes =
                    chunkIndexes.values().iterator();
            while (indexes.hasNext()) {
                SoftAreaIndexState index = indexes.next();
                index.pruneExpired(gameTime);
                if (index.size() == 0) {
                    indexes.remove();
                }
            }
        }

        private static long chunkKey(BlockPos position) {
            return ChunkPos.asLong(
                    position.getX() >> 4,
                    position.getZ() >> 4);
        }
    }
}
