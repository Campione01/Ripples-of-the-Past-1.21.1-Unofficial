package rotp.addons.d4c.travel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Nullable;

import rotp.addons.d4c.block.D4CPortalBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndGatewayBlock;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * 1.16 parallel-world terrain copy (am.java:231-438): arriving from the
 * overworld rebuilds the overworld blocks of the surrounding 9x9 chunks.
 */
public final class D4CParallelTerrainMirror {
    private static final TicketType<ChunkPos> MIRROR_TICKET =
            TicketType.create(
                    "rotp_d4c_parallel_mirror",
                    Comparator.comparingLong(ChunkPos::toLong),
                    100);
    /** Ticks to wait for a parallel chunk before loading it directly. */
    private static final int MAX_LOAD_WAIT = 100;
    private static final List<Job> JOBS = new ArrayList<>();

    private D4CParallelTerrainMirror() {}

    public static void onPlayerChangedDimension(
            ServerPlayer player,
            ResourceKey<Level> from,
            ResourceKey<Level> to) {
        if (!D4CParallelMirrorRules.shouldMirror(
                Level.OVERWORLD.equals(from),
                D4CDimensionTargets.PARALLEL_WORLD.equals(to.location()))) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        ServerLevel parallel = server.getLevel(to);
        if (overworld == null
                || parallel == null
                || player.level() != parallel) {
            return;
        }
        BlockPos feet = player.blockPosition();
        Job job = Job.snapshot(overworld, parallel, feet);
        if (job == null) {
            return;
        }
        // 1.16 writes the feet column at once, with block updates
        for (BlockPos pos : List.of(feet.below(), feet, feet.above())) {
            job.write(parallel, pos, Block.UPDATE_ALL);
        }
        liftIfBuried(player, parallel, job, feet);
        JOBS.add(job);
    }

    public static void tick(MinecraftServer server) {
        if (JOBS.isEmpty()) {
            return;
        }
        TickBudget budget = new TickBudget(System.nanoTime());
        Iterator<Job> iterator = JOBS.iterator();
        while (iterator.hasNext() && budget.open()) {
            Job job = iterator.next();
            ServerLevel level = server.getLevel(job.level);
            if (level == null || job.step(level, budget)) {
                iterator.remove();
            }
        }
    }

    public static void clear() {
        JOBS.clear();
    }

    static boolean isPortal(BlockState state) {
        Block block = state.getBlock();
        return block instanceof D4CPortalBlock
                || block instanceof NetherPortalBlock
                || block instanceof EndPortalBlock
                || block instanceof EndPortalFrameBlock
                || block instanceof EndGatewayBlock;
    }

    // Port safety: a buried arrival stands on the copied ground instead.
    private static void liftIfBuried(
            ServerPlayer player,
            ServerLevel level,
            Job job,
            BlockPos feet) {
        if (isClear(level, feet)) {
            return;
        }
        for (int y = job.minY; y <= job.maxY; y++) {
            job.write(
                    level,
                    new BlockPos(feet.getX(), y, feet.getZ()),
                    Block.UPDATE_CLIENTS);
        }
        for (int y = feet.getY() + 1; y < job.maxY; y++) {
            if (isClear(level, new BlockPos(feet.getX(), y, feet.getZ()))) {
                player.connection.teleport(
                        player.getX(),
                        y,
                        player.getZ(),
                        player.getYRot(),
                        player.getXRot());
                return;
            }
        }
    }

    private static boolean isClear(ServerLevel level, BlockPos feet) {
        BlockPos head = feet.above();
        return level.getBlockState(feet)
                        .getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(head)
                        .getCollisionShape(level, head).isEmpty();
    }

    private static final class TickBudget {
        final long start;
        int written;
        int finishedChunks;

        TickBudget(long start) {
            this.start = start;
        }

        boolean open() {
            return D4CParallelMirrorRules.withinTickBudget(
                    written,
                    finishedChunks,
                    System.nanoTime() - start);
        }
    }

    private static final class ChunkJob {
        final ChunkPos pos;
        final long order;
        @Nullable
        BlockState[] states;
        int cursor;

        ChunkJob(ChunkPos pos, BlockState[] states, long order) {
            this.pos = pos;
            this.states = states;
            this.order = order;
        }
    }

    private static final class Job {
        final ResourceKey<Level> level;
        final int minY;
        final int maxY;
        final List<ChunkJob> chunks;
        int chunkIndex;
        int waitTicks;

        private Job(
                ResourceKey<Level> level,
                int minY,
                int maxY,
                List<ChunkJob> chunks) {
            this.level = level;
            this.minY = minY;
            this.maxY = maxY;
            this.chunks = chunks;
        }

        @Nullable
        static Job snapshot(
                ServerLevel overworld,
                ServerLevel parallel,
                BlockPos feet) {
            int minY = D4CParallelMirrorRules.lowestY(
                    feet.getY(), parallel.getMinBuildHeight());
            int maxY = D4CParallelMirrorRules.highestY(
                    feet.getY(), parallel.getMaxBuildHeight());
            if (maxY < minY) {
                return null;
            }
            int centerX = feet.getX() >> 4;
            int centerZ = feet.getZ() >> 4;
            int radius = D4CParallelMirrorRules.CHUNK_RADIUS;
            BlockState air = Blocks.AIR.defaultBlockState();
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            List<ChunkJob> chunks = new ArrayList<>();
            for (int cx = centerX - radius; cx <= centerX + radius; cx++) {
                for (int cz = centerZ - radius;
                        cz <= centerZ + radius;
                        cz++) {
                    // the player just left; unloaded chunks are skipped
                    LevelChunk source =
                            overworld.getChunkSource().getChunkNow(cx, cz);
                    if (source == null) {
                        continue;
                    }
                    BlockState[] states =
                            new BlockState[(maxY - minY + 1) << 8];
                    int index = 0;
                    for (int y = minY; y <= maxY; y++) {
                        for (int lz = 0; lz < 16; lz++) {
                            for (int lx = 0; lx < 16; lx++) {
                                BlockState state = source.getBlockState(
                                        cursor.set(
                                                (cx << 4) + lx,
                                                y,
                                                (cz << 4) + lz));
                                states[index++] =
                                        D4CParallelMirrorRules.mirroredState(
                                                state,
                                                isPortal(state),
                                                air);
                            }
                        }
                    }
                    chunks.add(new ChunkJob(
                            new ChunkPos(cx, cz),
                            states,
                            D4CParallelMirrorRules.chunkOrder(
                                    cx - centerX, cz - centerZ)));
                }
            }
            if (chunks.isEmpty()) {
                return null;
            }
            chunks.sort(Comparator.comparingLong(chunk -> chunk.order));
            return new Job(parallel.dimension(), minY, maxY, chunks);
        }

        @Nullable
        BlockState targetAt(BlockPos pos) {
            if (pos.getY() < minY || pos.getY() > maxY) {
                return null;
            }
            int cx = pos.getX() >> 4;
            int cz = pos.getZ() >> 4;
            for (ChunkJob chunk : chunks) {
                if (chunk.pos.x == cx && chunk.pos.z == cz) {
                    BlockState[] states = chunk.states;
                    return states == null
                            ? null
                            : states[((pos.getY() - minY) << 8)
                                    | ((pos.getZ() & 15) << 4)
                                    | (pos.getX() & 15)];
                }
            }
            return null;
        }

        boolean write(ServerLevel level, BlockPos pos, int flags) {
            BlockState target = targetAt(pos);
            if (!D4CParallelMirrorRules.needsWrite(
                    target, level.getBlockState(pos))) {
                return false;
            }
            level.setBlock(pos, target, flags);
            return true;
        }

        /** Copies within the tick budget; true once every chunk is done. */
        boolean step(ServerLevel level, TickBudget budget) {
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            while (chunkIndex < chunks.size()) {
                if (!budget.open()) {
                    return false;
                }
                ChunkJob chunk = chunks.get(chunkIndex);
                // keeps the chunk loaded while it is copied
                level.getChunkSource().addRegionTicket(
                        MIRROR_TICKET, chunk.pos, 0, chunk.pos);
                LevelChunk target = level.getChunkSource()
                        .getChunkNow(chunk.pos.x, chunk.pos.z);
                if (target == null) {
                    if (++waitTicks < MAX_LOAD_WAIT) {
                        return false;
                    }
                    target = level.getChunk(chunk.pos.x, chunk.pos.z);
                }
                waitTicks = 0;
                BlockState[] states = chunk.states;
                int baseX = chunk.pos.getMinBlockX();
                int baseZ = chunk.pos.getMinBlockZ();
                while (states != null && chunk.cursor < states.length) {
                    if ((chunk.cursor & 63) == 0 && !budget.open()) {
                        return false;
                    }
                    int index = chunk.cursor++;
                    cursor.set(
                            baseX + (index & 15),
                            minY + (index >> 8),
                            baseZ + ((index >> 4) & 15));
                    BlockState state = states[index];
                    if (!D4CParallelMirrorRules.needsWrite(
                            state, target.getBlockState(cursor))) {
                        continue;
                    }
                    level.setBlock(
                            cursor.immutable(), state, Block.UPDATE_CLIENTS);
                    budget.written++;
                    if (!budget.open()) {
                        return false;
                    }
                }
                chunk.states = null;
                level.getChunkSource().removeRegionTicket(
                        MIRROR_TICKET, chunk.pos, 0, chunk.pos);
                chunkIndex++;
                if (D4CParallelMirrorRules.updatesNeighboursAfter(
                        ++budget.finishedChunks)) {
                    updateBorder(level, chunk.pos);
                }
            }
            // 1.16 relights each copied chunk once the job ends
            for (ChunkJob chunk : chunks) {
                level.getChunkSource().getLightEngine().checkBlock(
                        new BlockPos(
                                chunk.pos.getMinBlockX(),
                                64,
                                chunk.pos.getMinBlockZ()));
            }
            return true;
        }

        // 1.16 notifies the non-air blocks on the chunk's border ring
        private void updateBorder(ServerLevel level, ChunkPos pos) {
            int low = Math.max(level.getMinBuildHeight(), minY - 1);
            int high = Math.min(level.getMaxBuildHeight() - 1, maxY + 1);
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    if (lx != 0 && lx != 15 && lz != 0 && lz != 15) {
                        continue;
                    }
                    for (int y = low; y <= high; y++) {
                        BlockPos at = new BlockPos(
                                pos.getMinBlockX() + lx,
                                y,
                                pos.getMinBlockZ() + lz);
                        BlockState state = level.getBlockState(at);
                        if (!state.isAir()) {
                            level.updateNeighborsAt(at, state.getBlock());
                        }
                    }
                }
            }
        }
    }
}
