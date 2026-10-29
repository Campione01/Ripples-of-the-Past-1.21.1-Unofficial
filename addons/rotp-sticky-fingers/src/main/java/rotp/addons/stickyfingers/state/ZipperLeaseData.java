package rotp.addons.stickyfingers.state;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.util.functions.JojoModUtil;

import rotp.addons.stickyfingers.StickyFingersAddon;
import rotp.addons.stickyfingers.StickyFingersPhase1Spec;
import rotp.addons.stickyfingers.block.StickyFingersZipperBlock;
import rotp.addons.stickyfingers.blockentity.StickyFingersZipperBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class ZipperLeaseData extends SavedData {
    private static final String FILE_NAME =
            "rotp_stfn-zipper-leases";
    private static final String ACTIVE_TAG = "Active";
    private static final String HISTORY_TAG = "History";
    private static final int MAX_HISTORY = 64;
    // Bounds one walk over touching zippers, as the 1.16 neighbour-update
    // limit did.
    private static final int MAX_GROUP = 512;
    private static final TagKey<Block> CANNOT_RESTORE =
            TagKey.create(
                    Registries.BLOCK,
                    ResourceLocation.fromNamespaceAndPath(
                            "jojo_ripples",
                            "crazy_d_cannot_restore"));
    private static final int BLOCK_UPDATE_FLAGS =
            Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS;
    private static final SavedData.Factory<ZipperLeaseData> FACTORY =
            new SavedData.Factory<>(
                    ZipperLeaseData::new,
                    ZipperLeaseData::load);

    private final Map<UUID, ZipperLeaseRecord> activeById =
            new HashMap<>();
    private final Map<Long, UUID> activeByPosition = new HashMap<>();
    private final Deque<ZipperLeaseRecord> history =
            new ArrayDeque<>();
    private final Set<Long> chunksQueuedForReconciliation =
            new HashSet<>();
    private boolean recoverySweepRequired;

    public enum UseResult {
        CREATED(true),
        OPENED(true),
        CLOSED(true),
        RESTORED(true),
        REJECTED(false);

        private final boolean success;

        UseResult(boolean success) {
            this.success = success;
        }

        public boolean success() {
            return success;
        }
    }

    public static ZipperLeaseData get(ServerLevel level) {
        return level.getDataStorage()
                .computeIfAbsent(FACTORY, FILE_NAME);
    }

    public static ZipperLeaseData load(
            CompoundTag tag, HolderLookup.Provider registries) {
        ZipperLeaseData data = new ZipperLeaseData();
        readRecords(tag.getList(ACTIVE_TAG, Tag.TAG_COMPOUND))
                .forEach(data::loadActive);
        readRecords(tag.getList(HISTORY_TAG, Tag.TAG_COMPOUND))
                .forEach(data::appendHistory);
        data.recoverySweepRequired = !data.activeById.isEmpty();
        return data;
    }

    @Override
    public CompoundTag save(
            CompoundTag tag, HolderLookup.Provider registries) {
        ListTag active = new ListTag();
        activeById.values().stream()
                .sorted(Comparator.comparing(
                        record -> record.leaseId().toString()))
                .map(ZipperLeaseRecord::toNbt)
                .forEach(active::add);
        tag.put(ACTIVE_TAG, active);

        ListTag audit = new ListTag();
        history.stream()
                .map(ZipperLeaseRecord::toNbt)
                .forEach(audit::add);
        tag.put(HISTORY_TAG, audit);
        return tag;
    }

    public UseResult use(
            ServerPlayer owner, BlockHitResult hitResult) {
        ServerLevel level = owner.serverLevel();
        BlockPos position = hitResult.getBlockPos();
        BlockState state = level.getBlockState(position);
        if (state.is(StickyFingersAddon.STICKY_FINGERS_ZIPPER.get())) {
            return useExisting(owner, position, state);
        }
        return create(owner, position, hitResult, state);
    }

    // Held sweep: only places, never toggles or removes a zipper.
    public UseResult place(
            ServerPlayer owner, BlockHitResult hitResult) {
        BlockPos position = hitResult.getBlockPos();
        BlockState state = owner.serverLevel().getBlockState(position);
        if (state.is(StickyFingersAddon.STICKY_FINGERS_ZIPPER.get())) {
            return UseResult.REJECTED;
        }
        return create(owner, position, hitResult, state);
    }

    public void tick(ServerLevel level) {
        if (recoverySweepRequired) {
            recoverySweepRequired = false;
            for (ZipperLeaseRecord record :
                    List.copyOf(activeById.values())) {
                requestClose(
                        record,
                        ZipperCloseReason.SERVER_RESTART,
                        level.getGameTime());
            }
        }

        if (!chunksQueuedForReconciliation.isEmpty()) {
            for (ZipperLeaseRecord record :
                    List.copyOf(activeById.values())) {
                if (record.isPendingRestore()
                        && chunksQueuedForReconciliation.contains(
                                new ChunkPos(record.position())
                                        .toLong())) {
                    restoreIfLoaded(level, record, true);
                }
            }
            chunksQueuedForReconciliation.clear();
        }

        for (ZipperLeaseRecord initial :
                List.copyOf(activeById.values())) {
            ZipperLeaseRecord record =
                    activeById.get(initial.leaseId());
            if (record == null) {
                continue;
            }
            if (!record.dimension().equals(
                    level.dimension().location())) {
                record = requestClose(
                        record,
                        ZipperCloseReason.DIMENSION_MISMATCH,
                        level.getGameTime());
            } else if (!record.isPendingRestore()) {
                record = validateActiveLease(level, record);
            }
            if (record != null && record.isPendingRestore()) {
                restoreIfLoaded(level, record, true);
            }
        }
    }

    public void closeOwned(
            ServerLevel level,
            UUID ownerId,
            ZipperCloseReason reason) {
        for (ZipperLeaseRecord record :
                List.copyOf(activeById.values())) {
            if (!record.ownerId().equals(ownerId)) {
                continue;
            }
            ZipperLeaseRecord closing = requestClose(
                    record, reason, level.getGameTime());
            restoreIfLoaded(level, closing, true);
        }
    }

    public void onChunkUnload(
            ServerLevel level, ChunkPos chunkPosition) {
        long chunkKey = chunkPosition.toLong();
        for (ZipperLeaseRecord record :
                List.copyOf(activeById.values())) {
            if (chunkKey != new ChunkPos(record.position()).toLong()) {
                continue;
            }
            requestClose(
                    record,
                    ZipperCloseReason.CHUNK_UNLOAD,
                    level.getGameTime());
        }
    }

    public void onChunkLoad(ChunkPos chunkPosition) {
        chunksQueuedForReconciliation.add(chunkPosition.toLong());
    }

    public void reconcileBlockEntity(
            ServerLevel level,
            StickyFingersZipperBlockEntity blockEntity) {
        UUID leaseId = blockEntity.getLeaseId();
        ZipperLeaseRecord record =
                leaseId != null ? activeById.get(leaseId) : null;
        if (record == null) {
            ZipperLeaseRecord recovered =
                    blockEntity.toRecoveryRecord(
                            level.dimension().location(),
                            level.getGameTime());
            if (recovered == null
                    || activeByPosition.containsKey(
                            recovered.position().asLong())) {
                return;
            }
            putActive(recovered);
            setDirty();
            restoreIfLoaded(level, recovered, true);
            return;
        }
        if (!blockEntity.matches(record)) {
            ZipperLeaseRecord closing = requestClose(
                    record,
                    ZipperCloseReason.LEASE_CONFLICT,
                    level.getGameTime());
            restoreIfLoaded(level, closing, true);
        }
    }

    public void onZipperRemoved(
            ServerLevel level,
            BlockPos position,
            @Nullable StickyFingersZipperBlockEntity blockEntity) {
        ZipperLeaseRecord record = getAt(position);
        if (record == null || record.isPendingRestore()) {
            return;
        }
        if (blockEntity != null && !blockEntity.matches(record)) {
            return;
        }
        finalizeWithoutOverwrite(requestClose(
                record,
                ZipperCloseReason.BLOCK_REPLACED,
                level.getGameTime()));
    }

    public int activeCount() {
        return activeById.size();
    }

    public int historyCount() {
        return history.size();
    }

    @Nullable
    public ZipperLeaseRecord getAt(BlockPos position) {
        UUID leaseId = activeByPosition.get(position.asLong());
        return leaseId != null ? activeById.get(leaseId) : null;
    }

    private UseResult useExisting(
            ServerPlayer owner,
            BlockPos position,
            BlockState state) {
        ZipperLeaseRecord record = getAt(position);
        BlockEntity rawBlockEntity =
                owner.serverLevel().getBlockEntity(position);
        // 1.16 remove/toggle had no owner check: any active user may.
        if (record == null
                || !StickyFingersAddon.isStickyFingersActive(owner)
                || !(rawBlockEntity
                        instanceof StickyFingersZipperBlockEntity
                                blockEntity)
                || !blockEntity.matches(record)) {
            return UseResult.REJECTED;
        }

        ZipperLeaseRecord linked = linkedTo(record);
        // 1.16: a plain press removes, Shift+press (toggle_zipper) toggles.
        if (!owner.isShiftKeyDown()) {
            ZipperLeaseRecord closing = requestClose(
                    record,
                    ZipperCloseReason.MANUAL_CLOSE,
                    owner.serverLevel().getGameTime());
            boolean restored = restoreIfLoaded(
                    owner.serverLevel(), closing, true);
            // 1.16 destroy(): the linked zipper goes with it.
            if (linked != null) {
                restoreIfLoaded(
                        owner.serverLevel(),
                        activeById.get(linked.leaseId()),
                        false);
            }
            return restored
                    ? UseResult.RESTORED
                    : UseResult.REJECTED;
        }

        boolean opening = !record.open()
                && !state.getValue(StickyFingersZipperBlock.OPEN);
        ZipperLeaseRecord toggled =
                record.withOpen(opening);
        putActive(toggled);
        boolean changed = owner.serverLevel().setBlock(
                position,
                state.setValue(
                        StickyFingersZipperBlock.OPEN, opening),
                BLOCK_UPDATE_FLAGS);
        if (!changed) {
            putActive(record);
            return UseResult.REJECTED;
        }
        setDirty();
        syncGroup(owner.serverLevel(), position, opening);
        play(
                owner.serverLevel(),
                position,
                opening
                        ? StickyFingersAddon.ZIPPER_OPEN.get()
                        : StickyFingersAddon.ZIPPER_CLOSE.get());
        return opening ? UseResult.OPENED : UseResult.CLOSED;
    }

    private UseResult create(
            ServerPlayer owner,
            BlockPos position,
            BlockHitResult hitResult,
            BlockState originalState) {
        ServerLevel level = owner.serverLevel();
        if (!canCreate(owner, position, originalState)
                || leasesOwnedBy(owner.getUUID())
                        >= StickyFingersPhase1Spec.MAX_ZIPPER_LEASES) {
            return UseResult.REJECTED;
        }

        CompoundTag blockEntityTag =
                snapshotBlockEntity(level, position, originalState);
        if (blockEntityTag == null) {
            return UseResult.REJECTED;
        }

        // 1.16 holdTick/placeZippers: a wall one or two blocks thick with
        // a free cell behind it is zipped on both of its faces.
        Direction facing = hitResult.getDirection();
        Direction away = facing.getOpposite();
        BlockPos behind = position.relative(away);
        boolean through = false;
        ZipperLeaseRecord linked = null;
        if (isOpenSpace(level, behind)) {
            through = originalState.isFaceSturdy(level, position, away);
        } else if (isOpenSpace(level, behind.relative(away))) {
            BlockState backState = level.getBlockState(behind);
            CompoundTag backTag =
                    backState.isFaceSturdy(level, behind, away)
                            && leasesOwnedBy(owner.getUUID()) + 2
                                    <= StickyFingersPhase1Spec
                                            .MAX_ZIPPER_LEASES
                            && canCreate(owner, behind, backState)
                            ? snapshotBlockEntity(
                                    level, behind, backState)
                            : null;
            if (backTag != null) {
                linked = placeZipper(
                        level, owner, behind, backState, backTag,
                        away, false, position);
            }
        }

        // 1.16 holdTick: an in-ground user whose zipper is not a linked
        // pair gets a second one under or over it, for a two-high exit.
        boolean secondExit = through
                && away.getAxis().isHorizontal()
                && ZipperInGround.isInGround(owner)
                && !isDonorPair(level, position, originalState, facing);

        ZipperLeaseRecord record = placeZipper(
                level, owner, position, originalState, blockEntityTag,
                facing, through,
                linked != null ? linked.position() : null);
        if (record == null) {
            if (linked != null) {
                rollbackCreation(level, linked);
            }
            return UseResult.REJECTED;
        }
        if (secondExit) {
            placeSecondExit(level, owner, position, away);
        }
        // 1.16 updateShape: touching zippers of the same facing take the
        // state of the new, closed one.
        syncGroup(level, position, false);
        play(level, position, StickyFingersAddon.ZIPPER_CREATE.get());
        return UseResult.CREATED;
    }

    // 1.16 holdTick/getLinkedBlockPos, seen from an in-ground user, whose
    // targeted face is the far one: the pair needs a free cell one or two
    // blocks in front of the block, behind a sturdy face. The donor's
    // zipper cell is not free, and its hollow is still the wall block.
    private static boolean isDonorPair(
            ServerLevel level,
            BlockPos position,
            BlockState state,
            Direction front) {
        BlockPos near = position.relative(front);
        if (isOpenSpace(level, near)) {
            return state.isFaceSturdy(level, position, front);
        }
        BlockState nearState = level.getBlockState(near);
        return isOpenSpace(level, near.relative(front))
                && (nearState.is(
                                StickyFingersAddon.STICKY_FINGERS_ZIPPER.get())
                        || nearState.isFaceSturdy(level, near, front));
    }

    private void placeSecondExit(
            ServerLevel level,
            ServerPlayer owner,
            BlockPos position,
            Direction exit) {
        BlockPos cell = position.relative(exit);
        BlockPos second;
        if (isOpenSpace(level, cell.below())) {
            second = position.below();
        } else if (isOpenSpace(level, cell.above())) {
            second = position.above();
        } else {
            return;
        }
        BlockState state = level.getBlockState(second);
        if (leasesOwnedBy(owner.getUUID())
                        >= StickyFingersPhase1Spec.MAX_ZIPPER_LEASES
                || !canCreate(owner, second, state)) {
            return;
        }
        CompoundTag blockEntityTag =
                snapshotBlockEntity(level, second, state);
        if (blockEntityTag != null) {
            placeZipper(
                    level, owner, second, state, blockEntityTag,
                    exit, false, null);
        }
    }

    @Nullable
    private ZipperLeaseRecord placeZipper(
            ServerLevel level,
            ServerPlayer owner,
            BlockPos position,
            BlockState originalState,
            CompoundTag blockEntityTag,
            Direction facing,
            boolean through,
            @Nullable BlockPos linkedPosition) {
        long createdAt = level.getGameTime();
        ZipperLeaseRecord record = new ZipperLeaseRecord(
                UUID.randomUUID(),
                owner.getUUID(),
                level.dimension().location(),
                position.immutable(),
                createdAt,
                StickyFingersPhase1Spec.ZIPPER_NO_EXPIRY,
                false,
                NbtUtils.writeBlockState(originalState),
                blockEntityTag.isEmpty() ? null : blockEntityTag,
                null,
                0L,
                linkedPosition);
        putActive(record);
        setDirty();

        BlockState zipperState =
                StickyFingersAddon.STICKY_FINGERS_ZIPPER
                        .get()
                        .defaultBlockState()
                        .setValue(
                                StickyFingersZipperBlock.FACING,
                                facing)
                        .setValue(
                                StickyFingersZipperBlock.OPEN,
                                false)
                        .setValue(
                                StickyFingersZipperBlock.THROUGH,
                                through);
        if (!level.setBlock(
                position, zipperState, BLOCK_UPDATE_FLAGS)) {
            removeActive(record);
            setDirty();
            return null;
        }

        BlockEntity placed = level.getBlockEntity(position);
        if (!(placed
                instanceof StickyFingersZipperBlockEntity zipperEntity)) {
            rollbackCreation(level, record);
            return null;
        }
        zipperEntity.initialize(record);
        return record;
    }

    // The full NBT of a safe block entity, an empty tag for a block
    // without one, null when the block cannot be leased.
    @Nullable
    private static CompoundTag snapshotBlockEntity(
            ServerLevel level, BlockPos position, BlockState state) {
        BlockEntity blockEntity = level.getBlockEntity(position);
        if (blockEntity == null) {
            return state.hasBlockEntity() ? null : new CompoundTag();
        }
        if (!isSafeBlockEntity(blockEntity)) {
            return null;
        }
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
    }

    // 1.16 ZipperUtil.isBlockFree: free space that holds no zipper.
    private static boolean isOpenSpace(
            ServerLevel level, BlockPos cell) {
        return ZipperInGround.isBlockFree(level, cell)
                && !ZipperInGround.isZipperFront(level, cell);
    }

    @Nullable
    private ZipperLeaseRecord linkedTo(
            @Nullable ZipperLeaseRecord record) {
        if (record == null || record.linkedPosition() == null) {
            return null;
        }
        ZipperLeaseRecord linked = getAt(record.linkedPosition());
        return linked != null
                && record.position().equals(linked.linkedPosition())
                ? linked
                : null;
    }

    // 1.16 updateShape: the linked zipper and every touching zipper of
    // the same facing copy the open state.
    private void syncGroup(
            ServerLevel level, BlockPos origin, boolean open) {
        Deque<BlockPos> pending = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        pending.add(origin.immutable());
        seen.add(origin.immutable());
        while (!pending.isEmpty()) {
            BlockPos position = pending.removeFirst();
            BlockState state = level.getBlockState(position);
            setOpen(level, position, state, open);
            for (BlockPos near : BlockPos.betweenClosed(
                    position.offset(-1, -1, -1),
                    position.offset(1, 1, 1))) {
                if (seen.size() < MAX_GROUP
                        && !seen.contains(near)
                        && isLoaded(level, near)
                        && touches(
                                level,
                                position,
                                state,
                                near,
                                level.getBlockState(near))) {
                    seen.add(near.immutable());
                    pending.add(near.immutable());
                }
            }
            ZipperLeaseRecord linked = linkedTo(getAt(position));
            if (linked != null && seen.add(linked.position())) {
                pending.add(linked.position());
            }
        }
    }

    private void setOpen(
            ServerLevel level,
            BlockPos position,
            BlockState state,
            boolean open) {
        if (!state.is(StickyFingersAddon.STICKY_FINGERS_ZIPPER.get())) {
            return;
        }
        if (state.getValue(StickyFingersZipperBlock.OPEN) != open) {
            level.setBlock(
                    position,
                    state.setValue(StickyFingersZipperBlock.OPEN, open),
                    BLOCK_UPDATE_FLAGS);
        }
        ZipperLeaseRecord record = getAt(position);
        if (record != null
                && !record.isPendingRestore()
                && record.open() != open) {
            putActive(record.withOpen(open));
            setDirty();
        }
    }

    // Zippers on the same side of their blocks touch sideways and
    // diagonally. A zipper whose faced cell is itself a zipper block is
    // the port's own stacking, which the donor's zipper cell cannot
    // hold: on that side it joins no group, so zipping onward from
    // inside an opening never closes the opening.
    private static boolean touches(
            ServerLevel level,
            BlockPos position,
            BlockState state,
            BlockPos near,
            BlockState other) {
        for (Direction side : Direction.values()) {
            if (StickyFingersZipperBlock.faces(state, side)
                    && StickyFingersZipperBlock.faces(other, side)
                    && !isZipper(level, position.relative(side))
                    && !isZipper(level, near.relative(side))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isZipper(ServerLevel level, BlockPos position) {
        return isLoaded(level, position)
                && level.getBlockState(position)
                        .is(StickyFingersAddon.STICKY_FINGERS_ZIPPER.get());
    }

    private boolean canCreate(
            ServerPlayer owner,
            BlockPos position,
            BlockState state) {
        ServerLevel level = owner.serverLevel();
        return StickyFingersAddon.isStickyFingersActive(owner)
                && level.getWorldBorder().isWithinBounds(position)
                && getAt(position) == null
                && !state.isAir()
                && state.getFluidState().isEmpty()
                && state.getDestroySpeed(level, position) >= 0.0F
                && !state.is(CANNOT_RESTORE)
                && (state.isCollisionShapeFullBlock(
                                level, position)
                        || isSafeBlockEntity(
                                level.getBlockEntity(position)))
                && JojoModUtil.breakingBlocksEnabled(level)
                && JojoModUtil.canEntityDestroy(
                        level, position, state, owner);
    }

    private ZipperLeaseRecord validateActiveLease(
            ServerLevel level, ZipperLeaseRecord record) {
        long gameTime = level.getGameTime();
        // No TIMEOUT close: 1.16 zippers had no lifetime.
        ServerPlayer owner = level.getServer().getPlayerList()
                .getPlayer(record.ownerId());
        if (owner == null) {
            return requestClose(
                    record,
                    ZipperCloseReason.OWNER_LOGOUT,
                    gameTime);
        }
        if (!owner.isAlive() || owner.isRemoved()) {
            return requestClose(
                    record,
                    ZipperCloseReason.OWNER_DEATH,
                    gameTime);
        }
        if (owner.level() != level) {
            return requestClose(
                    record,
                    ZipperCloseReason.DIMENSION_CHANGE,
                    gameTime);
        }
        if (!StickyFingersAddon.isStickyFingersActive(owner)) {
            return requestClose(
                    record,
                    ZipperCloseReason.ABILITY_LOST,
                    gameTime);
        }
        if (owner.distanceToSqr(Vec3.atCenterOf(record.position()))
                > StickyFingersPhase1Spec.MAX_ZIPPER_OWNER_DISTANCE_SQR) {
            return requestClose(
                    record,
                    ZipperCloseReason.OWNER_TOO_FAR,
                    gameTime);
        }
        if (!isLoaded(level, record.position())) {
            return record;
        }

        BlockState current = level.getBlockState(record.position());
        BlockState original = readOriginalState(level, record);
        if (current.equals(original)) {
            ZipperLeaseRecord closing = requestClose(
                    record,
                    ZipperCloseReason.BLOCK_REPLACED,
                    gameTime);
            if (restoreOriginalBlockEntity(
                    level, closing, original)) {
                finalizeRestored(closing, false, level);
                return null;
            }
            return closing;
        }
        if (!current.is(
                StickyFingersAddon.STICKY_FINGERS_ZIPPER.get())) {
            finalizeWithoutOverwrite(requestClose(
                    record,
                    ZipperCloseReason.BLOCK_REPLACED,
                    gameTime));
            return null;
        }

        BlockEntity raw = level.getBlockEntity(record.position());
        if (!(raw
                instanceof StickyFingersZipperBlockEntity blockEntity)
                || !blockEntity.matches(record)) {
            return requestClose(
                    record,
                    ZipperCloseReason.LEASE_CONFLICT,
                    gameTime);
        }
        boolean worldOpen =
                current.getValue(StickyFingersZipperBlock.OPEN);
        if (worldOpen != record.open()) {
            level.setBlock(
                    record.position(),
                    current.setValue(
                            StickyFingersZipperBlock.OPEN,
                            record.open()),
                    BLOCK_UPDATE_FLAGS);
        }
        return record;
    }

    private boolean restoreIfLoaded(
            ServerLevel level,
            ZipperLeaseRecord record,
            boolean playSound) {
        if (record == null
                || !ZipperRestorationPolicy.canAttempt(
                        record.isPendingRestore(),
                        isLoaded(level, record.position()))) {
            return false;
        }

        BlockState original = readOriginalState(level, record);
        if (original.isAir()) {
            return false;
        }
        BlockState current = level.getBlockState(record.position());
        ZipperRestorationPolicy.ObservedState observedState =
                current.equals(original)
                        ? ZipperRestorationPolicy.ObservedState
                                .ORIGINAL_SNAPSHOT
                        : current.is(
                                StickyFingersAddon
                                        .STICKY_FINGERS_ZIPPER
                                        .get())
                                ? ZipperRestorationPolicy.ObservedState
                                        .OWNED_ZIPPER
                                : ZipperRestorationPolicy.ObservedState
                                        .FOREIGN_BLOCK;
        return switch (
                ZipperRestorationPolicy.decideLoaded(
                        observedState)) {
            case RESTORE_BLOCK_ENTITY -> {
                if (!restoreOriginalBlockEntity(
                        level, record, original)) {
                    yield false;
                }
                finalizeRestored(record, playSound, level);
                yield true;
            }
            case FINALIZE_WITHOUT_OVERWRITE -> {
                finalizeWithoutOverwrite(record);
                yield false;
            }
            case RESTORE_SNAPSHOT -> {
                if (!level.setBlock(
                                record.position(),
                                original,
                                BLOCK_UPDATE_FLAGS)
                        || !restoreOriginalBlockEntity(
                                level, record, original)) {
                    yield false;
                }
                finalizeRestored(record, playSound, level);
                yield true;
            }
        };
    }

    private boolean restoreOriginalBlockEntity(
            ServerLevel level,
            ZipperLeaseRecord record,
            BlockState originalState) {
        CompoundTag blockEntityTag = record.originalBlockEntity();
        BlockEntity restored =
                level.getBlockEntity(record.position());
        if (blockEntityTag == null) {
            return restored == null;
        }
        if (restored == null) {
            return false;
        }
        BlockEntity decoded = BlockEntity.loadStatic(
                record.position(),
                originalState,
                blockEntityTag.copy(),
                level.registryAccess());
        if (decoded == null
                || decoded.getType() != restored.getType()) {
            return false;
        }
        try {
            restored.loadWithComponents(
                    blockEntityTag, level.registryAccess());
            restored.setChanged();
            level.sendBlockUpdated(
                    record.position(),
                    originalState,
                    originalState,
                    Block.UPDATE_CLIENTS);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void rollbackCreation(
            ServerLevel level, ZipperLeaseRecord record) {
        ZipperLeaseRecord closing = requestClose(
                record,
                ZipperCloseReason.CREATION_FAILED,
                level.getGameTime());
        restoreIfLoaded(level, closing, false);
    }

    private ZipperLeaseRecord requestClose(
            ZipperLeaseRecord record,
            ZipperCloseReason reason,
            long gameTime) {
        ZipperLeaseRecord current =
                activeById.get(record.leaseId());
        if (current == null) {
            return record;
        }
        ZipperLeaseRecord closing =
                current.requestClose(reason, gameTime);
        if (closing != current) {
            putActive(closing);
            setDirty();
            // 1.16 destroy(): a zipper never outlives its linked zipper.
            ZipperLeaseRecord linked = linkedTo(closing);
            if (linked != null) {
                requestClose(linked, reason, gameTime);
            }
        }
        return closing;
    }

    private void finalizeRestored(
            ZipperLeaseRecord record,
            boolean playSound,
            ServerLevel level) {
        removeActive(record);
        appendHistory(record);
        setDirty();
        if (!playSound) {
            return;
        }
        if (record.closeReason()
                        == ZipperCloseReason.MANUAL_CLOSE
                && record.open()) {
            play(
                    level,
                    record.position(),
                    StickyFingersAddon.ZIPPER_CLOSE.get());
        }
        play(
                level,
                record.position(),
                StickyFingersAddon.ZIPPER_REMOVE.get());
    }

    private void finalizeWithoutOverwrite(
            ZipperLeaseRecord record) {
        removeActive(record);
        appendHistory(record);
        setDirty();
    }

    private void putActive(ZipperLeaseRecord record) {
        ZipperLeaseRecord previous =
                activeById.put(record.leaseId(), record);
        if (previous != null
                && !previous.position().equals(record.position())) {
            activeByPosition.remove(
                    previous.position().asLong(),
                    previous.leaseId());
        }
        activeByPosition.put(
                record.position().asLong(), record.leaseId());
    }

    private void removeActive(ZipperLeaseRecord record) {
        activeById.remove(record.leaseId());
        activeByPosition.remove(
                record.position().asLong(), record.leaseId());
    }

    private void loadActive(ZipperLeaseRecord record) {
        UUID existingId =
                activeByPosition.get(record.position().asLong());
        ZipperLeaseRecord existing = existingId != null
                ? activeById.get(existingId)
                : null;
        if (existing != null
                && existing.createdAt() >= record.createdAt()) {
            return;
        }
        if (existing != null) {
            activeById.remove(existing.leaseId());
        }
        putActive(record);
    }

    private void appendHistory(ZipperLeaseRecord record) {
        if (!record.isPendingRestore()) {
            return;
        }
        history.addLast(record);
        while (history.size() > MAX_HISTORY) {
            history.removeFirst();
        }
    }

    private int leasesOwnedBy(UUID ownerId) {
        int count = 0;
        for (ZipperLeaseRecord record : activeById.values()) {
            if (record.ownerId().equals(ownerId)) {
                count++;
            }
        }
        return count;
    }

    private static List<ZipperLeaseRecord> readRecords(
            ListTag records) {
        List<ZipperLeaseRecord> decoded = new ArrayList<>();
        for (Tag element : records) {
            if (!(element instanceof CompoundTag recordTag)) {
                continue;
            }
            ZipperLeaseRecord record =
                    ZipperLeaseRecord.fromNbt(recordTag);
            if (record != null) {
                decoded.add(record);
            }
        }
        return decoded;
    }

    private static BlockState readOriginalState(
            ServerLevel level, ZipperLeaseRecord record) {
        return NbtUtils.readBlockState(
                level.registryAccess()
                        .lookupOrThrow(Registries.BLOCK),
                record.originalState());
    }

    private static boolean isSafeBlockEntity(
            @Nullable BlockEntity blockEntity) {
        if (blockEntity == null) {
            return false;
        }
        BlockEntityType<?> type = blockEntity.getType();
        return type == BlockEntityType.SIGN
                || type == BlockEntityType.HANGING_SIGN
                || type == BlockEntityType.BANNER
                || type == BlockEntityType.SKULL;
    }

    private static boolean isLoaded(
            ServerLevel level, BlockPos position) {
        return level.getChunkSource().getChunkNow(
                        position.getX() >> 4,
                        position.getZ() >> 4)
                != null;
    }

    private static void play(
            ServerLevel level,
            BlockPos position,
            SoundEvent sound) {
        level.playSound(
                null,
                position,
                sound,
                SoundSource.PLAYERS,
                1.0F,
                1.0F);
    }
}
