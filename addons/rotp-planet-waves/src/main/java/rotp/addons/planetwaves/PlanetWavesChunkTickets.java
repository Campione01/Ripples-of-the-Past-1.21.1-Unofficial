package rotp.addons.planetwaves;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;

import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * Chunks a game test fixture holds itself (the pattern of rotp-king-crimson's KingCrimsonChunkTickets). The framework
 * holds only the chunk of the one-block template, and its grid puts that template on any block of a chunk, so the
 * entities of a fixture often stand in other chunks. There an added entity stays in a hidden section until the chunk
 * is accessible, so level.getEntity does not find it and a target kept by its id resolves to nothing; and it ticks only
 * in a chunk with a ticking ticket. The fixture therefore holds every chunk within MARGIN blocks of the box it uses at
 * the entity-ticking level, with a ticket value of its own, and returns once all of them tick entities. A newly held
 * chunk gets there only after the ring around it has generated, which takes real time under load; the game test server
 * does not pace its ticks, so the budget is real time and the server thread runs the chunk tasks meanwhile, no game
 * tick passing. The tickets go when the fixture closes, at the latest when its test ends.
 */
final class PlanetWavesChunkTickets implements GameTestListener {
    static final double MARGIN = 3.0D;
    // Level 31 is the entity-ticking level, the one the template's chunk gets.
    static final int TICKET_RADIUS = 2;
    static final TicketType<UUID> TICKET = TicketType.create("rotp_pw_gametest", Comparator.<UUID>naturalOrder());
    private static final long WAIT_NANOS = 30_000_000_000L;
    private static final long PAUSE_NANOS = 1_000_000L;
    private final ServerLevel level;
    private final UUID id = UUID.randomUUID();
    private final Set<ChunkPos> chunks = new LinkedHashSet<>();

    PlanetWavesChunkTickets(GameTestHelper helper) {
        level = helper.getLevel();
        helper.testInfo.addListener(this);
    }

    /** Every chunk within MARGIN blocks of the box between the two corners; returns once they all tick entities. */
    PlanetWavesChunkTickets hold(Vec3 corner, Vec3 otherCorner) {
        int minX = SectionPos.blockToSectionCoord(Mth.floor(Math.min(corner.x, otherCorner.x) - MARGIN));
        int maxX = SectionPos.blockToSectionCoord(Mth.floor(Math.max(corner.x, otherCorner.x) + MARGIN));
        int minZ = SectionPos.blockToSectionCoord(Mth.floor(Math.min(corner.z, otherCorner.z) - MARGIN));
        int maxZ = SectionPos.blockToSectionCoord(Mth.floor(Math.max(corner.z, otherCorner.z) + MARGIN));
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
        return this;
    }

    List<ChunkPos> notTickingEntities() {
        List<ChunkPos> idle = new ArrayList<>();
        for (ChunkPos chunk : chunks) {
            if (!level.isPositionEntityTicking(chunk.getWorldPosition())) {
                idle.add(chunk);
            }
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
            if (!cache.pollTask()) {
                LockSupport.parkNanos(PAUSE_NANOS);
            }
        }
    }

    void release() {
        for (ChunkPos chunk : chunks) {
            level.getChunkSource().removeRegionTicket(TICKET, chunk, TICKET_RADIUS, id);
        }
        chunks.clear();
    }

    @Override
    public void testStructureLoaded(GameTestInfo test) {}

    @Override
    public void testPassed(GameTestInfo test, GameTestRunner runner) {
        release();
    }

    @Override
    public void testFailed(GameTestInfo test, GameTestRunner runner) {
        release();
    }

    @Override
    public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {
        release();
    }
}
