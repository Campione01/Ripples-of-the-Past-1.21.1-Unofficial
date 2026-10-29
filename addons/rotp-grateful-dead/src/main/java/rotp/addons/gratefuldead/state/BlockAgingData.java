package rotp.addons.gratefuldead.state;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import rotp.addons.gratefuldead.GratefulDeadAddon;
import rotp.addons.gratefuldead.network.BlockAgingPayload;
import rotp.addons.gratefuldead.service.AgingService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.network.PacketDistributor;

public final class BlockAgingData extends SavedData {
    public static final int MAX_BLOCK_RECORDS = 65_536;
    public static final int MAX_RECORDS_PER_CHUNK = 256;
    public static final int MAX_RECORD_TICKS_PER_TICK = 128;
    public static final int MAX_SPREADS_PER_TICK = 24;
    public static final int MAX_PENDING_SPREADS = 4_096;
    public static final int OWNER_FADE_TICKS = 60;
    private static final String FILE_NAME =
            "rotp_grateful_dead-block_aging";
    private static final String RECORDS_TAG = "Records";
    private static final SavedData.Factory<BlockAgingData>
            FACTORY = new SavedData.Factory<>(
                    BlockAgingData::new,
                    BlockAgingData::load);

    private final Map<Long, Entry> entries =
            new HashMap<>();
    private final Map<Long, LinkedHashSet<Long>> byChunk =
            new HashMap<>();
    private final ArrayDeque<Long> tickQueue =
            new ArrayDeque<>();
    private final Set<Long> queued = new HashSet<>();
    private final ArrayDeque<Spread> spreadQueue =
            new ArrayDeque<>();

    public static BlockAgingData get(ServerLevel level) {
        return level.getDataStorage()
                .computeIfAbsent(FACTORY, FILE_NAME);
    }

    public static BlockAgingData load(
            CompoundTag tag,
            HolderLookup.Provider registries) {
        BlockAgingData data = new BlockAgingData();
        for (Tag element :
                tag.getList(RECORDS_TAG, Tag.TAG_COMPOUND)) {
            if (data.entries.size() >= MAX_BLOCK_RECORDS) {
                break;
            }
            if (!(element instanceof CompoundTag entryTag)
                    || !entryTag.hasUUID("Owner")) {
                continue;
            }
            BlockPos position =
                    BlockPos.of(entryTag.getLong("Position"));
            float progress = Mth.clamp(
                    entryTag.getFloat("Progress"),
                    0.0F,
                    1.0F);
            boolean fading =
                    entryTag.getBoolean("Fading");
            int fadeTicksLeft = fading
                    ? Mth.clamp(
                            entryTag.contains(
                                            "FadeTicksLeft")
                                    ? entryTag.getInt(
                                            "FadeTicksLeft")
                                    : OWNER_FADE_TICKS,
                            1,
                            OWNER_FADE_TICKS)
                    : 0;
            float fadePerTick = fading
                    ? Mth.clamp(
                            entryTag.getFloat(
                                    "FadePerTick"),
                            0.0F,
                            1.0F)
                    : 0.0F;
            if (fading && fadePerTick <= 0.0F) {
                fadePerTick =
                        progress / fadeTicksLeft;
            }
            Entry entry = new Entry(
                    position,
                    entryTag.getUUID("Owner"),
                    Math.max(
                            0L,
                            entryTag.getLong(
                                    "OwnerGeneration")),
                    progress,
                    fading,
                    fadePerTick,
                    fadeTicksLeft);
            long chunkKey =
                    new ChunkPos(position).toLong();
            Set<Long> chunkEntries =
                    data.byChunk.get(chunkKey);
            if (entry.progress() > 0.0F
                    && (chunkEntries == null
                            || chunkEntries.size()
                                    < MAX_RECORDS_PER_CHUNK)) {
                data.insert(entry);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(
            CompoundTag tag,
            HolderLookup.Provider registries) {
        ListTag records = new ListTag();
        entries.values().stream()
                .sorted(Comparator.comparingLong(
                        entry -> entry.position().asLong()))
                .forEach(entry -> {
                    CompoundTag entryTag = new CompoundTag();
                    entryTag.putLong(
                            "Position",
                            entry.position().asLong());
                    entryTag.putUUID("Owner", entry.owner());
                    entryTag.putLong(
                            "OwnerGeneration",
                            entry.ownerGeneration());
                    entryTag.putFloat(
                            "Progress", entry.progress());
                    entryTag.putBoolean(
                            "Fading", entry.fading());
                    entryTag.putFloat(
                            "FadePerTick",
                            entry.fadePerTick());
                    entryTag.putInt(
                            "FadeTicksLeft",
                            entry.fadeTicksLeft());
                    records.add(entryTag);
                });
        tag.put(RECORDS_TAG, records);
        return tag;
    }

    public float progress(BlockPos position) {
        Entry entry = entries.get(position.asLong());
        return entry != null ? entry.progress() : 0.0F;
    }

    public Entry entry(BlockPos position) {
        return entries.get(position.asLong());
    }

    public int recordCount() {
        return entries.size();
    }

    public boolean age(
            ServerLevel level,
            BlockPos position,
            UUID owner,
            long generation,
            float amount) {
        if (amount <= 0.0F
                || !level.hasChunkAt(position)
                || !AgingService.isAgeableBlock(
                        level.getBlockState(position))) {
            return false;
        }
        long key = position.asLong();
        Entry previous = entries.get(key);
        if (previous != null
                && !previous.owner().equals(owner)) {
            return false;
        }
        if (previous == null) {
            if (entries.size() >= MAX_BLOCK_RECORDS) {
                return false;
            }
            long chunkKey =
                    new ChunkPos(position).toLong();
            if (byChunk.getOrDefault(
                                    chunkKey,
                                    new LinkedHashSet<>())
                            .size()
                    >= MAX_RECORDS_PER_CHUNK) {
                return false;
            }
        }
        float nextProgress = Mth.clamp(
                (previous != null
                                ? previous.progress()
                                : 0.0F)
                        + amount,
                0.0F,
                1.0F);
        Entry next = new Entry(
                position.immutable(),
                owner,
                generation,
                nextProgress,
                false,
                0.0F,
                0);
        replace(next);
        setDirty();
        sync(level, next);
        return true;
    }

    public void inherit(
            ServerLevel level,
            BlockPos position,
            ItemAgingState itemState) {
        if (itemState.progress() <= 0.0F
                || itemState.owner().isEmpty()) {
            return;
        }
        age(
                level,
                position,
                itemState.owner().get(),
                itemState.ownerGeneration(),
                itemState.progress());
    }

    public void remove(
            ServerLevel level, BlockPos position) {
        Entry removed =
                entries.remove(position.asLong());
        if (removed == null) {
            return;
        }
        removeIndexes(removed);
        setDirty();
        clearClient(level, position);
    }

    public boolean crumbleOnContact(
            ServerLevel level, BlockPos position) {
        Entry entry = entries.get(position.asLong());
        if (entry == null || entry.progress() < 1.0F) {
            return false;
        }
        crumble(level, entry);
        return true;
    }

    public void tick(ServerLevel level) {
        int recordBudget = Math.min(
                MAX_RECORD_TICKS_PER_TICK,
                tickQueue.size());
        for (int index = 0;
                index < recordBudget;
                index++) {
            Long key = tickQueue.pollFirst();
            if (key == null) {
                break;
            }
            queued.remove(key);
            Entry entry = entries.get(key);
            if (entry == null) {
                continue;
            }
            tickEntry(level, entry);
            if (entries.containsKey(key)) {
                enqueue(key);
            }
        }

        for (int index = 0;
                index < MAX_SPREADS_PER_TICK;
                index++) {
            Spread spread = spreadQueue.pollFirst();
            if (spread == null) {
                break;
            }
            age(
                    level,
                    spread.position(),
                    spread.owner(),
                    spread.ownerGeneration(),
                    spread.amount());
        }
    }

    public void syncChunk(
            ServerPlayer player, ChunkPos chunk) {
        Set<Long> positions =
                byChunk.get(chunk.toLong());
        if (positions == null) {
            return;
        }
        int sent = 0;
        for (long key : positions) {
            Entry entry = entries.get(key);
            if (entry != null) {
                PacketDistributor.sendToPlayer(
                        player,
                        payload(entry));
                if (++sent >= MAX_RECORDS_PER_CHUNK) {
                    break;
                }
            }
        }
    }

    private void tickEntry(
            ServerLevel level, Entry entry) {
        if (!level.hasChunkAt(entry.position())) {
            return;
        }
        BlockState state =
                level.getBlockState(entry.position());
        if (!AgingService.isAgeableBlock(state)) {
            remove(level, entry.position());
            return;
        }
        boolean removalRequested =
                AgingRemovalData.get(level.getServer())
                                .generation(entry.owner())
                        > entry.ownerGeneration();
        ServerPlayer ownerPlayer = level.getServer()
                .getPlayerList()
                .getPlayer(entry.owner());
        boolean outOfRange = ownerPlayer == null
                || !AgingService.hasGratefulDead(ownerPlayer)
                || ownerPlayer.level() != level
                || ownerPlayer.distanceToSqr(
                                entry.position().getX() + 0.5D,
                                entry.position().getY() + 0.5D,
                                entry.position().getZ() + 0.5D)
                        > AgingService.AURA_RADIUS
                                * AgingService.AURA_RADIUS;
        if (removalRequested
                || outOfRange
                || entry.fading()) {
            int fadeTicksLeft = entry.fading()
                    ? entry.fadeTicksLeft()
                    : OWNER_FADE_TICKS;
            float fadePerTick = entry.fading()
                    ? entry.fadePerTick()
                    : entry.progress()
                            / OWNER_FADE_TICKS;
            float nextProgress = Math.max(
                    0.0F,
                    entry.progress()
                            - fadePerTick);
            int nextFadeTicksLeft =
                    fadeTicksLeft - 1;
            if (nextProgress <= 0.0F
                    || nextFadeTicksLeft <= 0) {
                remove(level, entry.position());
                return;
            }
            Entry next = new Entry(
                    entry.position(),
                    entry.owner(),
                    entry.ownerGeneration(),
                    nextProgress,
                    true,
                    fadePerTick,
                    nextFadeTicksLeft);
            entries.put(
                    entry.position().asLong(), next);
            setDirty();
            sync(level, next);
            return;
        }
        if (entry.progress() >= 1.0F) {
            crumble(level, entry);
        }
    }

    private void crumble(
            ServerLevel level, Entry entry) {
        BlockPos position = entry.position();
        BlockState state = level.getBlockState(position);
        if (state.is(BlockTags.LOGS)
                || state.is(BlockTags.LEAVES)
                || state.is(BlockTags.REPLACEABLE_BY_TREES)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.SUGAR_CANE)
                || state.is(Blocks.BAMBOO)
                || state.is(Blocks.VINE)) {
            queueLivingNeighbors(level, entry);
            level.destroyBlock(position, false);
        }
        else if (state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.FARMLAND)
                || state.is(Blocks.DIRT_PATH)) {
            level.setBlockAndUpdate(
                    position,
                    Blocks.DIRT.defaultBlockState());
        }
        else {
            level.destroyBlock(position, false);
        }
        if (level.random.nextInt(3) == 0) {
            net.minecraft.world.Containers.dropItemStack(
                    level,
                    position.getX() + 0.5D,
                    position.getY() + 0.5D,
                    position.getZ() + 0.5D,
                    new ItemStack(
                            GratefulDeadAddon
                                    .PLANT_AGING_DUST.get()));
        }
        remove(level, position);
    }

    private void queueLivingNeighbors(
            ServerLevel level, Entry source) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbor =
                    source.position().relative(direction);
            if (level.hasChunkAt(neighbor)
                    && AgingService.isAgeableBlock(
                            level.getBlockState(neighbor))
                    && spreadQueue.size()
                            < MAX_PENDING_SPREADS) {
                spreadQueue.addLast(new Spread(
                        neighbor.immutable(),
                        source.owner(),
                        source.ownerGeneration(),
                        0.25F));
            }
        }
    }

    private void insert(Entry entry) {
        entries.put(entry.position().asLong(), entry);
        long chunkKey =
                new ChunkPos(entry.position()).toLong();
        byChunk.computeIfAbsent(
                        chunkKey,
                        ignored -> new LinkedHashSet<>())
                .add(entry.position().asLong());
        enqueue(entry.position().asLong());
    }

    private void replace(Entry entry) {
        if (entries.containsKey(entry.position().asLong())) {
            entries.put(entry.position().asLong(), entry);
            enqueue(entry.position().asLong());
        }
        else {
            insert(entry);
        }
    }

    private void enqueue(long key) {
        if (queued.add(key)) {
            tickQueue.addLast(key);
        }
    }

    private void removeIndexes(Entry entry) {
        long key = entry.position().asLong();
        queued.remove(key);
        long chunkKey =
                new ChunkPos(entry.position()).toLong();
        Set<Long> chunkEntries = byChunk.get(chunkKey);
        if (chunkEntries != null) {
            chunkEntries.remove(key);
            if (chunkEntries.isEmpty()) {
                byChunk.remove(chunkKey);
            }
        }
    }

    private static void sync(
            ServerLevel level, Entry entry) {
        PacketDistributor.sendToPlayersTrackingChunk(
                level,
                new ChunkPos(entry.position()),
                payload(entry));
    }

    private static BlockAgingPayload payload(Entry entry) {
        int stage = Mth.clamp(
                (int) Math.floor(
                        entry.progress() * 10.0F),
                0,
                9);
        return new BlockAgingPayload(
                entry.position(), entry.progress(), stage);
    }

    private static void clearClient(
            ServerLevel level, BlockPos position) {
        PacketDistributor.sendToPlayersTrackingChunk(
                level,
                new ChunkPos(position),
                new BlockAgingPayload(position, 0.0F, -1));
    }

    public record Entry(
            BlockPos position,
            UUID owner,
            long ownerGeneration,
            float progress,
            boolean fading,
            float fadePerTick,
            int fadeTicksLeft) {}

    private record Spread(
            BlockPos position,
            UUID owner,
            long ownerGeneration,
            float amount) {}
}
