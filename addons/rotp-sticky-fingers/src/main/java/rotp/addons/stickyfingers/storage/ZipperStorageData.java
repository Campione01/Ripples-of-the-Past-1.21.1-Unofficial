package rotp.addons.stickyfingers.storage;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import rotp.addons.stickyfingers.StickyFingersAddon;
import rotp.addons.stickyfingers.StickyFingersPhase1Spec;
import rotp.addons.stickyfingers.menu.AbstractZipperStorageMenu;
import rotp.addons.stickyfingers.menu.BlockZipperStorageMenu;
import rotp.addons.stickyfingers.menu.EntityZipperStorageMenu;
import rotp.addons.stickyfingers.state.ZipperLeaseData;
import rotp.addons.stickyfingers.state.ZipperLeaseRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ZipperStorageData extends SavedData {
    public enum OpenResult {
        OPENED,
        INVALID_TARGET,
        NOT_OWNER
    }

    private static final String FILE_NAME =
            "rotp_stfn-storage";
    private static final String ENTITY_STORAGES_TAG =
            "EntityStorages";
    private static final String BLOCK_STORAGES_TAG =
            "BlockStorages";
    private static final String DROP_PENDING_TAG = "DropPending";
    private static final SavedData.Factory<ZipperStorageData>
            FACTORY = new SavedData.Factory<>(
                    ZipperStorageData::new,
                    ZipperStorageData::load);

    private final Map<UUID, EntityZipperStorageRecord>
            entityStorages = new HashMap<>();
    private final Map<
            BlockZipperStorageKey,
            BlockZipperStorageRecord> blockStorages =
                    new HashMap<>();
    private final Set<BlockZipperStorageKey> pendingBlockDrops = new HashSet<>();

    public static ZipperStorageData get(
            MinecraftServer server) {
        return server.overworld()
                .getDataStorage()
                .computeIfAbsent(FACTORY, FILE_NAME);
    }

    public static ZipperStorageData load(
            CompoundTag tag,
            HolderLookup.Provider registries) {
        ZipperStorageData data = new ZipperStorageData();
        for (Tag element :
                tag.getList(
                        ENTITY_STORAGES_TAG,
                        Tag.TAG_COMPOUND)) {
            if (!(element instanceof CompoundTag recordTag)) {
                continue;
            }
            EntityZipperStorageRecord record =
                    EntityZipperStorageRecord.fromNbt(
                            recordTag,
                            registries,
                            data::setDirty);
            if (record != null) {
                data.entityStorages.put(
                        record.targetId(), record);
            }
        }
        for (Tag element :
                tag.getList(
                        BLOCK_STORAGES_TAG,
                        Tag.TAG_COMPOUND)) {
            if (!(element instanceof CompoundTag recordTag)) {
                continue;
            }
            BlockZipperStorageRecord record =
                    BlockZipperStorageRecord.fromNbt(
                            recordTag,
                            registries,
                            data::setDirty);
            if (record != null) {
                data.blockStorages.put(record.key(), record);
                if (recordTag.getBoolean(DROP_PENDING_TAG)) {
                    data.pendingBlockDrops.add(record.key());
                }
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(
            CompoundTag tag,
            HolderLookup.Provider registries) {
        ListTag entities = new ListTag();
        entityStorages.values().stream()
                .sorted(Comparator.comparing(
                        record ->
                                record.targetId().toString()))
                .map(record -> record.toNbt(registries))
                .forEach(entities::add);
        tag.put(ENTITY_STORAGES_TAG, entities);

        ListTag blocks = new ListTag();
        blockStorages.values().stream()
                .sorted(Comparator
                        .comparing(
                                (BlockZipperStorageRecord record) ->
                                        record.key()
                                                .dimension()
                                                .toString())
                        .thenComparingLong(
                                record ->
                                        record.key()
                                                .position()
                                                .asLong()))
                .map(record -> {
                    CompoundTag recordTag = record.toNbt(registries);
                    saveDropPending(recordTag, pendingBlockDrops.contains(record.key()));
                    return recordTag;
                })
                .forEach(blocks::add);
        tag.put(BLOCK_STORAGES_TAG, blocks);
        return tag;
    }

    public OpenResult openEntityStorage(
            ServerPlayer viewer,
            LivingEntity target,
            boolean inner) {
        if (!validEntityOpen(viewer, target, inner)) {
            return OpenResult.INVALID_TARGET;
        }
        // Upstream owns this storage by target UUID, not by opener.
        EntityZipperStorageRecord record =
                entityStorages.get(target.getUUID());
        if (record == null) {
            record = new EntityZipperStorageRecord(
                    target.getUUID(),
                    viewer.getUUID(),
                    viewer.serverLevel().getGameTime(),
                    new ZipperStorageInventory(
                            StickyFingersPhase1Spec
                                    .ENTITY_STORAGE_SLOTS,
                            this::setDirty));
            entityStorages.put(target.getUUID(), record);
            setDirty();
        }
        ZipperStorageSession session =
                ZipperStorageSession.entity(
                        viewer, target, inner);
        EntityZipperStorageRecord openedRecord = record;
        boolean opened = viewer.openMenu(
                        new SimpleMenuProvider(
                                (containerId,
                                        inventory,
                                        player) ->
                                        new EntityZipperStorageMenu(
                                                containerId,
                                                inventory,
                                                openedRecord
                                                        .inventory(),
                                                session),
                                target.getDisplayName()))
                .isPresent();
        if (opened) {
            playOpen(viewer);
            return OpenResult.OPENED;
        }
        return OpenResult.INVALID_TARGET;
    }

    public OpenResult openBlockStorage(
            ServerPlayer viewer, BlockPos position) {
        ServerLevel level = viewer.serverLevel();
        if (!validBlockOpen(viewer, position)) {
            return OpenResult.INVALID_TARGET;
        }
        BlockZipperStorageKey key =
                new BlockZipperStorageKey(
                        level.dimension().location(),
                        position.immutable());
        ZipperLeaseRecord lease =
                ZipperLeaseData.get(level).getAt(position);
        if (level.getBlockState(position).is(
                        StickyFingersAddon
                                .STICKY_FINGERS_ZIPPER.get())
                && (lease == null
                        || !lease.ownerId().equals(
                                viewer.getUUID()))) {
            return OpenResult.NOT_OWNER;
        }
        BlockZipperStorageRecord record =
                blockStorages.get(key);
        if (record != null
                && !record.ownerId().equals(
                        viewer.getUUID())) {
            return OpenResult.NOT_OWNER;
        }
        if (record == null) {
            record = new BlockZipperStorageRecord(
                    key,
                    viewer.getUUID(),
                    level.getGameTime(),
                    new ZipperStorageInventory(
                            StickyFingersPhase1Spec
                                    .BLOCK_STORAGE_SLOTS,
                            this::setDirty));
            blockStorages.put(key, record);
            setDirty();
        }
        ZipperStorageSession session =
                ZipperStorageSession.block(viewer, key);
        BlockZipperStorageRecord openedRecord = record;
        Component title =
                level.getBlockState(position)
                        .getBlock().getName();
        boolean opened = viewer.openMenu(
                        new SimpleMenuProvider(
                                (containerId,
                                        inventory,
                                        player) ->
                                        new BlockZipperStorageMenu(
                                                containerId,
                                                inventory,
                                                openedRecord
                                                        .inventory(),
                                                session),
                                title))
                .isPresent();
        if (opened) {
            playOpen(viewer);
            return OpenResult.OPENED;
        }
        return OpenResult.INVALID_TARGET;
    }

    public void tick(MinecraftServer server) {
        for (BlockZipperStorageRecord record :
                List.copyOf(blockStorages.values())) {
            ServerLevel level =
                    findLevel(server, record.key().dimension());
            ChunkPos chunk = new ChunkPos(record.key().position());
            LevelChunk loadedChunk = level != null
                    ? level.getChunkSource().getChunkNow(chunk.x, chunk.z) : null;
            if (shouldRemoveBlockStorage(record.key(), loadedChunk)) {
                removeBlockStorage(
                        server, level, record.key());
            }
        }
        for (ServerPlayer player :
                List.copyOf(
                        server.getPlayerList().getPlayers())) {
            if (player.containerMenu
                            instanceof AbstractZipperStorageMenu
                                    menu
                    && !menu.stillValid(player)) {
                player.closeContainer();
            }
        }
    }

    public void closeViewer(ServerPlayer viewer) {
        if (viewer.containerMenu
                instanceof AbstractZipperStorageMenu menu) {
            ZipperStorageSession session = menu.session();
            if (session != null
                    && session.referencesViewer(
                            viewer.getUUID())) {
                viewer.closeContainer();
            }
        }
    }

    public void closeEntitySessions(
            MinecraftServer server, UUID targetId) {
        for (ServerPlayer viewer :
                List.copyOf(
                        server.getPlayerList().getPlayers())) {
            if (viewer.containerMenu
                    instanceof AbstractZipperStorageMenu menu) {
                ZipperStorageSession session =
                        menu.session();
                if (session != null
                        && session.referencesTarget(targetId)) {
                    viewer.closeContainer();
                }
            }
        }
    }

    public void onTargetDeath(
            ServerLevel level, LivingEntity target) {
        MinecraftServer server = level.getServer();
        closeEntitySessions(server, target.getUUID());
        if (target instanceof Player
                && level.getGameRules().getBoolean(
                        GameRules.RULE_KEEPINVENTORY)) {
            return;
        }
        EntityZipperStorageRecord record =
                entityStorages.remove(target.getUUID());
        if (record == null) {
            return;
        }
        List<ItemStack> drained =
                record.inventory().drain();
        setDirty();
        drop(level, target.position(), drained);
    }

    public void onTargetDiscarded(
            ServerLevel level, LivingEntity target) {
        closeEntitySessions(
                level.getServer(), target.getUUID());
        EntityZipperStorageRecord record =
                entityStorages.remove(target.getUUID());
        if (record == null) {
            return;
        }
        List<ItemStack> drained =
                record.inventory().drain();
        setDirty();
        drop(level, target.position(), drained);
    }

    public void onBlockBroken(
            ServerLevel level, BlockPos position) {
        BlockZipperStorageKey key =
                new BlockZipperStorageKey(
                        level.dimension().location(),
                        position.immutable());
        removeBlockStorage(
                level.getServer(), level, key);
    }

    public void onChunkUnload(
            ServerLevel level, ChunkAccess unloadingChunk) {
        ChunkPos chunk = unloadingChunk.getPos();
        MinecraftServer server = level.getServer();
        for (ServerPlayer viewer :
                List.copyOf(
                        server.getPlayerList().getPlayers())) {
            if (viewer.containerMenu
                    instanceof AbstractZipperStorageMenu menu) {
                ZipperStorageSession session =
                        menu.session();
                if (session != null
                        && session.referencesChunk(
                                level, chunk)) {
                    viewer.closeContainer();
                }
            }
        }
        markMissingBlockStorages(level.dimension().location(), chunk, unloadingChunk);
    }

    void markMissingBlockStorages(
            ResourceLocation dimension, ChunkPos chunk, BlockGetter unloadingChunk) {
        for (BlockZipperStorageRecord record :
                List.copyOf(blockStorages.values())) {
            if (missingInUnloadingChunk(record.key(), dimension, chunk,
                    position -> unloadingChunk.getBlockState(position).isAir())) {
                // Spawning drops here can re-enter the unloading chunk. Keep its inventory durable until a loaded tick.
                if (pendingBlockDrops.add(record.key())) {
                    setDirty();
                }
            }
        }
    }

    boolean shouldRemoveBlockStorage(BlockZipperStorageKey key, @Nullable BlockGetter loadedChunk) {
        return shouldDropLoadedStorage(loadedChunk != null, pendingBlockDrops.contains(key),
                () -> loadedChunk.getBlockState(key.position()).isAir());
    }

    static boolean missingInUnloadingChunk(
            BlockZipperStorageKey key, ResourceLocation dimension, ChunkPos chunk, Predicate<BlockPos> isAir) {
        return key.dimension().equals(dimension) && new ChunkPos(key.position()).equals(chunk)
                && isAir.test(key.position());
    }

    static boolean shouldDropLoadedStorage(boolean loaded, boolean pending, BooleanSupplier isAir) {
        return loaded && (pending || isAir.getAsBoolean());
    }

    static void saveDropPending(CompoundTag recordTag, boolean pending) {
        if (pending) {
            recordTag.putBoolean(DROP_PENDING_TAG, true);
        } else {
            recordTag.remove(DROP_PENDING_TAG);
        }
    }

    public boolean hasEntityStorage(UUID targetId) {
        return entityStorages.containsKey(targetId);
    }

    public boolean canAccessBlockStorage(
            BlockZipperStorageKey key, UUID viewerId) {
        BlockZipperStorageRecord record =
                blockStorages.get(key);
        return record != null
                && record.ownerId().equals(viewerId);
    }

    public int entityStorageCount() {
        return entityStorages.size();
    }

    public int blockStorageCount() {
        return blockStorages.size();
    }

    private void removeBlockStorage(
            MinecraftServer server,
            ServerLevel level,
            BlockZipperStorageKey key) {
        closeBlockSessions(server, key);
        pendingBlockDrops.remove(key);
        BlockZipperStorageRecord record =
                blockStorages.remove(key);
        if (record == null) {
            return;
        }
        List<ItemStack> drained =
                record.inventory().drain();
        setDirty();
        drop(
                level,
                Vec3.atCenterOf(key.position()),
                drained);
    }

    private void closeBlockSessions(
            MinecraftServer server,
            BlockZipperStorageKey key) {
        for (ServerPlayer viewer :
                List.copyOf(
                        server.getPlayerList().getPlayers())) {
            if (viewer.containerMenu
                    instanceof AbstractZipperStorageMenu menu) {
                ZipperStorageSession session =
                        menu.session();
                if (session != null
                        && session.referencesBlock(key)) {
                    viewer.closeContainer();
                }
            }
        }
    }

    private static boolean validEntityOpen(
            ServerPlayer viewer,
            LivingEntity target,
            boolean inner) {
        return StickyFingersAddon.isStickyFingersActive(viewer)
                && target.level() == viewer.level()
                && target.isAlive()
                && !target.isRemoved()
                && (!inner
                        || target.getUUID().equals(
                                viewer.getUUID()))
                && target.getBoundingBox().distanceToSqr(
                        viewer.getEyePosition())
                        <= StickyFingersPhase1Spec
                                .STORAGE_RANGE_SQR;
    }

    private static boolean validBlockOpen(
            ServerPlayer viewer, BlockPos position) {
        ServerLevel level = viewer.serverLevel();
        return StickyFingersAddon.isStickyFingersActive(viewer)
                && level.hasChunkAt(position)
                && !level.getBlockState(position).isAir()
                && new AABB(position).distanceToSqr(
                        viewer.getEyePosition())
                        <= StickyFingersPhase1Spec
                                .STORAGE_RANGE_SQR;
    }

    private static void playOpen(ServerPlayer viewer) {
        viewer.serverLevel().playSound(
                null,
                viewer,
                StickyFingersAddon.ZIPPER_OPEN.get(),
                SoundSource.PLAYERS,
                1.0F,
                1.0F);
    }

    private static void drop(
            ServerLevel level,
            Vec3 position,
            List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            Containers.dropItemStack(
                    level,
                    position.x,
                    position.y,
                    position.z,
                    stack);
        }
    }

    @Nullable
    private static ServerLevel findLevel(
            MinecraftServer server,
            ResourceLocation dimension) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().equals(dimension)) {
                return level;
            }
        }
        return null;
    }
}
