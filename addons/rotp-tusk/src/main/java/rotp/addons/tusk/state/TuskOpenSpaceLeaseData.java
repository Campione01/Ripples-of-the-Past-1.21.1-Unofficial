package rotp.addons.tusk.state;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.util.functions.JojoModUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;

public final class TuskOpenSpaceLeaseData extends SavedData {
    public static final int OPEN_TICKS = 200;
    private static final String FILE_NAME =
            "rotp_t-open-space-leases";
    private static final String LEASES_TAG = "Leases";
    private static final int BLOCK_UPDATE_FLAGS =
            Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS;
    private static final SavedData.Factory<TuskOpenSpaceLeaseData>
            FACTORY = new SavedData.Factory<>(
                    TuskOpenSpaceLeaseData::new,
                    TuskOpenSpaceLeaseData::load);

    private final Map<Long, Lease> leases = new HashMap<>();

    public static TuskOpenSpaceLeaseData get(ServerLevel level) {
        return level.getDataStorage()
                .computeIfAbsent(FACTORY, FILE_NAME);
    }

    public static TuskOpenSpaceLeaseData load(
            CompoundTag tag, HolderLookup.Provider registries) {
        TuskOpenSpaceLeaseData data =
                new TuskOpenSpaceLeaseData();
        ListTag list = tag.getList(LEASES_TAG, Tag.TAG_COMPOUND);
        for (Tag entry : list) {
            if (entry instanceof CompoundTag leaseTag) {
                Lease lease = Lease.fromNbt(leaseTag);
                if (lease != null) {
                    Lease existing =
                            data.leases.get(lease.position().asLong());
                    if (existing == null
                            || existing.createdAt()
                                    < lease.createdAt()) {
                        data.leases.put(
                                lease.position().asLong(), lease);
                    }
                }
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(
            CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        leases.values().stream()
                .sorted(Comparator.comparingLong(
                        lease -> lease.position().asLong()))
                .map(Lease::toNbt)
                .forEach(list::add);
        tag.put(LEASES_TAG, list);
        return tag;
    }

    public boolean canOpen(
            ServerLevel level,
            LivingEntity owner,
            BlockPos position) {
        if (leases.containsKey(position.asLong())
                || !level.getWorldBorder().isWithinBounds(position)) {
            return false;
        }
        BlockState state = level.getBlockState(position);
        if (state.isAir()
                || state.is(Blocks.BEDROCK)
                || !state.getFluidState().isEmpty()
                || state.getDestroySpeed(level, position) < 0.0F
                || !JojoModUtil.breakingBlocksEnabled(level)
                || !JojoModUtil.canEntityDestroy(
                        level, position, state, owner)) {
            return false;
        }
        BlockEntity blockEntity = level.getBlockEntity(position);
        return !state.hasBlockEntity()
                || blockEntity != null
                        && snapshotBlockEntity(
                                level,
                                position,
                                state,
                                blockEntity) != null;
    }

    public int open(
            ServerLevel level,
            LivingEntity owner,
            List<BlockPos> positions) {
        int opened = 0;
        long createdAt = level.getGameTime();
        for (BlockPos mutablePosition : positions) {
            BlockPos position = mutablePosition.immutable();
            if (!canOpen(level, owner, position)) {
                break;
            }

            BlockState originalState =
                    level.getBlockState(position);
            BlockEntity originalBlockEntity =
                    level.getBlockEntity(position);
            CompoundTag blockEntityTag =
                    originalBlockEntity != null
                            ? snapshotBlockEntity(
                                    level,
                                    position,
                                    originalState,
                                    originalBlockEntity)
                            : null;
            if (originalBlockEntity != null
                    && blockEntityTag == null) {
                break;
            }

            if (originalBlockEntity != null) {
                level.removeBlockEntity(position);
            }
            if (!level.setBlock(
                    position,
                    Blocks.AIR.defaultBlockState(),
                    BLOCK_UPDATE_FLAGS)) {
                restoreSnapshot(
                        level,
                        position,
                        originalState,
                        blockEntityTag);
                break;
            }

            leases.put(
                    position.asLong(),
                    new Lease(
                            owner.getUUID(),
                            position,
                            createdAt,
                            createdAt + OPEN_TICKS,
                            NbtUtils.writeBlockState(originalState),
                            blockEntityTag));
            opened++;
        }
        if (opened > 0) {
            setDirty();
        }
        return opened;
    }

    public void tick(ServerLevel level) {
        boolean changed = false;
        long gameTime = level.getGameTime();
        for (Lease lease :
                new ArrayList<>(leases.values())) {
            BlockPos position = lease.position();
            if (!isLoaded(level, position)) {
                continue;
            }

            BlockState current = level.getBlockState(position);
            BlockState original = readOriginalState(level, lease);
            if (!current.isAir()) {
                leases.remove(position.asLong());
                changed = true;
                continue;
            }
            if (gameTime < lease.expiresAt()) {
                continue;
            }
            if (!level.getEntitiesOfClass(
                            LivingEntity.class,
                            new AABB(position),
                            LivingEntity::isAlive)
                    .isEmpty()) {
                continue;
            }
            if (restoreSnapshot(
                    level,
                    position,
                    original,
                    lease.originalBlockEntity())) {
                leases.remove(position.asLong());
                changed = true;
            }
        }
        if (changed) {
            setDirty();
        }
    }

    public int activeCount() {
        return leases.size();
    }

    @Nullable
    private static CompoundTag snapshotBlockEntity(
            ServerLevel level,
            BlockPos position,
            BlockState state,
            BlockEntity blockEntity) {
        try {
            CompoundTag tag = blockEntity.saveWithFullMetadata(
                    level.registryAccess());
            BlockEntity decoded = BlockEntity.loadStatic(
                    position,
                    state,
                    tag.copy(),
                    level.registryAccess());
            return decoded != null
                            && decoded.getType() == blockEntity.getType()
                    ? tag
                    : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static boolean restoreSnapshot(
            ServerLevel level,
            BlockPos position,
            BlockState originalState,
            @Nullable CompoundTag blockEntityTag) {
        BlockState current = level.getBlockState(position);
        if (current.isAir()
                && !level.setBlock(
                        position,
                        originalState,
                        BLOCK_UPDATE_FLAGS)) {
            return false;
        }
        if (!level.getBlockState(position).equals(originalState)) {
            return false;
        }

        BlockEntity restored = level.getBlockEntity(position);
        if (blockEntityTag == null) {
            return restored == null;
        }
        if (restored == null) {
            return false;
        }
        try {
            BlockEntity decoded = BlockEntity.loadStatic(
                    position,
                    originalState,
                    blockEntityTag.copy(),
                    level.registryAccess());
            if (decoded == null
                    || decoded.getType() != restored.getType()) {
                return false;
            }
            restored.loadWithComponents(
                    blockEntityTag.copy(), level.registryAccess());
            restored.setChanged();
            level.sendBlockUpdated(
                    position,
                    originalState,
                    originalState,
                    Block.UPDATE_CLIENTS);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static BlockState readOriginalState(
            ServerLevel level, Lease lease) {
        return NbtUtils.readBlockState(
                level.registryAccess()
                        .lookupOrThrow(Registries.BLOCK),
                lease.originalState());
    }

    private static boolean isLoaded(
            ServerLevel level, BlockPos position) {
        return level.getChunkSource().getChunkNow(
                        position.getX() >> 4,
                        position.getZ() >> 4)
                != null;
    }

    private record Lease(
            UUID ownerId,
            BlockPos position,
            long createdAt,
            long expiresAt,
            CompoundTag originalState,
            @Nullable CompoundTag originalBlockEntity) {
        private Lease {
            originalState = originalState.copy();
            originalBlockEntity = originalBlockEntity != null
                    ? originalBlockEntity.copy()
                    : null;
        }

        @Override
        public CompoundTag originalState() {
            return originalState.copy();
        }

        @Override
        @Nullable
        public CompoundTag originalBlockEntity() {
            return originalBlockEntity != null
                    ? originalBlockEntity.copy()
                    : null;
        }

        private CompoundTag toNbt() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("OwnerId", ownerId);
            tag.putLong("Position", position.asLong());
            tag.putLong("CreatedAt", createdAt);
            tag.putLong("ExpiresAt", expiresAt);
            tag.put("OriginalState", originalState.copy());
            if (originalBlockEntity != null) {
                tag.put(
                        "OriginalBlockEntity",
                        originalBlockEntity.copy());
            }
            return tag;
        }

        @Nullable
        private static Lease fromNbt(CompoundTag tag) {
            if (!tag.hasUUID("OwnerId")
                    || !tag.contains("Position")
                    || !tag.contains("CreatedAt")
                    || !tag.contains("ExpiresAt")
                    || !tag.contains("OriginalState")) {
                return null;
            }
            long createdAt = tag.getLong("CreatedAt");
            long expiresAt = tag.getLong("ExpiresAt");
            if (expiresAt < createdAt) {
                return null;
            }
            return new Lease(
                    tag.getUUID("OwnerId"),
                    BlockPos.of(tag.getLong("Position")),
                    createdAt,
                    expiresAt,
                    tag.getCompound("OriginalState"),
                    tag.contains("OriginalBlockEntity")
                            ? tag.getCompound(
                                    "OriginalBlockEntity")
                            : null);
        }
    }
}
