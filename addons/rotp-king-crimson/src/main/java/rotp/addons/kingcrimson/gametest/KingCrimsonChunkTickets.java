package rotp.addons.kingcrimson.gametest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * Chunks a fixture holds: every chunk within MARGIN blocks of a position, because a body or projectile moves beside
 * its user and only a chunk held at the entity-ticking level ticks its entities here. A newly held chunk ticks
 * entities only after the ring around it has generated, which takes real time under load, so force() returns once all
 * of them tick. The game test server does not pace its ticks, hence the budget is real time and the server thread runs
 * the chunk tasks meanwhile. The tickets are the fixture's own (a ticket value of its own, not the level's forced
 * chunks): a fixture that releases late, as a listener does after the next batch has started, cannot take a chunk
 * from another fixture, and the end of a batch cannot take one from this.
 */
final class KingCrimsonChunkTickets {
    static final double MARGIN = 3.0D;
    // Level 31 is the entity-ticking level, the one a forced chunk gets.
    static final int TICKET_RADIUS = 2;
    static final TicketType<UUID> TICKET =
            TicketType.create("rotp_king_crimson_gametest", Comparator.<UUID>naturalOrder());
    private static final long WAIT_NANOS = 30_000_000_000L;
    private static final long PAUSE_NANOS = 1_000_000L;
    private final ServerLevel level;
    private final UUID id = UUID.randomUUID();
    private final Set<ChunkPos> chunks = new LinkedHashSet<>();

    KingCrimsonChunkTickets(ServerLevel level) { this.level = level; }

    void force(Vec3 position) {
        int minX = SectionPos.blockToSectionCoord(Mth.floor(position.x - MARGIN));
        int maxX = SectionPos.blockToSectionCoord(Mth.floor(position.x + MARGIN));
        int minZ = SectionPos.blockToSectionCoord(Mth.floor(position.z - MARGIN));
        int maxZ = SectionPos.blockToSectionCoord(Mth.floor(position.z + MARGIN));
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                ChunkPos key = new ChunkPos(x, z);
                if (chunks.add(key)) {
                    level.getChunkSource().addRegionTicket(TICKET, key, TICKET_RADIUS, id);
                    level.getChunk(key.x, key.z);
                }
            }
        }
        await();
    }

    List<ChunkPos> notTickingEntities() {
        List<ChunkPos> idle = new ArrayList<>();
        for (ChunkPos chunk : chunks) {
            if (!level.isPositionEntityTicking(chunk.getWorldPosition())) idle.add(chunk);
        }
        return idle;
    }

    private void await() {
        ServerChunkCache cache = level.getChunkSource();
        long start = System.nanoTime();
        List<ChunkPos> idle;
        while (!(idle = notTickingEntities()).isEmpty()) {
            long waited = System.nanoTime() - start;
            if (waited > WAIT_NANOS) {
                throw new GameTestAssertException("The held chunks " + idle + " do not tick entities after "
                        + waited / 1_000_000L + " ms");
            }
            if (!cache.pollTask()) LockSupport.parkNanos(PAUSE_NANOS);
        }
    }

    void release() {
        RuntimeException failure = null;
        for (ChunkPos chunk : chunks) {
            try { level.getChunkSource().removeRegionTicket(TICKET, chunk, TICKET_RADIUS, id); }
            catch (RuntimeException error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
        }
        chunks.clear();
        if (failure != null) throw failure;
    }
}
