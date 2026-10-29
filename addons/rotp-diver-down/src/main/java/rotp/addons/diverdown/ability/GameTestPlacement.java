package rotp.addons.diverdown.ability;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * Fixture positions of the Diver Down game tests: the owner one block off the test origin on x and z, the target
 * {@code distance} blocks further south. The layout never changes with the origin, so a fixture stays inside the
 * 6 by 7 block lane the test grid gives each test.
 *
 * <p>The "empty" template is 1x1x1, so the framework only makes the origin's chunk tick entities, and the origin is
 * random in every run. When the layout reaches into a neighbouring chunk, that chunk gets a ticket of this class and
 * the test body starts once it ticks entities.
 */
final class GameTestPlacement {
    // The game test server runs ticks as fast as it can, so a tick count alone is a race against the chunk workers.
    // The wait fails only when both budgets are used up; every waiting tick lasts at least a millisecond.
    static final int WAIT_TICKS = 4000;
    static final long WAIT_NANOS = 30_000_000_000L;
    /** Upper bound of the waiting ticks: at most one per millisecond of {@link #WAIT_NANOS}, or {@link #WAIT_TICKS}. */
    static final int WAIT_TEST_TICKS = 30_100;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long PAUSE_NANOS = 1_000_000L;
    // Chunk level 31 ticks entities, as a forced chunk does. A ticket of an own type, keyed by the test origin, is
    // used instead of ServerLevel.setChunkForced because that flag is one boolean per chunk: the framework sets it
    // for every test structure and other fixtures clear it, so clearing it here could stop a neighbouring test.
    private static final int ENTITY_TICKING_RADIUS = 2;
    private static final TicketType<BlockPos> FIXTURE_TICKET =
            TicketType.create("rotp_dd_gametest_fixture", BlockPos::compareTo);

    final Vec3 owner;
    final Vec3 target;

    private GameTestPlacement(Vec3 owner, Vec3 target) {
        this.owner = owner;
        this.target = target;
    }

    /**
     * Runs {@code body} once every chunk the fixture stands in ticks entities: at once when that is only the origin's
     * chunk, otherwise after the wait. The tickets are released when the test ends, however it ends.
     */
    static void whenTicking(GameTestHelper helper, int distance, Consumer<GameTestPlacement> body) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        Vec3 owner = helper.absoluteVec(new Vec3(1, 2, 1));
        GameTestPlacement placement = new GameTestPlacement(owner, owner.add(0, 0, distance));
        List<ChunkPos> neighbours = neighbourChunks(origin, distance);
        if (neighbours.isEmpty()) {
            body.accept(placement);
            return;
        }
        for (ChunkPos chunk : neighbours) {
            level.getChunkSource().addRegionTicket(FIXTURE_TICKET, chunk, ENTITY_TICKING_RADIUS, origin);
        }
        helper.testInfo.addListener(new GameTestListener() {
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
            public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {}

            private void release() {
                for (ChunkPos chunk : neighbours) {
                    level.getChunkSource().removeRegionTicket(FIXTURE_TICKET, chunk, ENTITY_TICKING_RADIUS, origin);
                }
            }
        });
        await(helper, origin, neighbours, 0, System.nanoTime(), () -> body.accept(placement));
    }

    /**
     * The chunks other than the origin's that hold the owner, the target or the idle Stand. The Stand idles within a
     * block of the owner, towards the origin on z, so the block columns from the origin to the target cover all three.
     */
    private static List<ChunkPos> neighbourChunks(BlockPos origin, int distance) {
        ChunkPos home = new ChunkPos(origin);
        ChunkPos far = new ChunkPos(origin.offset(1, 0, 1 + distance));
        List<ChunkPos> chunks = new ArrayList<>();
        for (int x = home.x; x <= far.x; x++) {
            for (int z = home.z; z <= far.z; z++) {
                if (x != home.x || z != home.z) {
                    chunks.add(new ChunkPos(x, z));
                }
            }
        }
        return chunks;
    }

    private static void await(GameTestHelper helper, BlockPos origin, List<ChunkPos> chunks, int waitedTicks,
            long startNanos, Runnable body) {
        ServerLevel level = helper.getLevel();
        ChunkPos pending = null;
        for (ChunkPos chunk : chunks) {
            if (!level.isPositionEntityTicking(chunk.getWorldPosition())) {
                pending = chunk;
                break;
            }
        }
        long waitedNanos = System.nanoTime() - startNanos;
        if (pending == null) {
            LOGGER.info("Diver Down fixture at origin {} reaches into {}: they tick entities after {} ticks, {} ms",
                    origin, chunks, waitedTicks, waitedNanos / 1_000_000L);
            body.run();
            return;
        }
        helper.assertTrue(waitedTicks < WAIT_TICKS || waitedNanos < WAIT_NANOS,
                "Chunk " + pending + " next to the test origin " + origin + " holds a fixture and did not start to"
                        + " tick entities within " + WAIT_TICKS + " ticks and " + WAIT_NANOS / 1_000_000_000L
                        + " s (waited " + waitedTicks + " ticks, " + waitedNanos / 1_000_000L + " ms)");
        pause();
        helper.runAfterDelay(1, () -> await(helper, origin, chunks, waitedTicks + 1, startNanos, body));
    }

    // Thread.sleep, not LockSupport.parkNanos: a pending unpark permit of the server thread ends a park at once.
    private static void pause() {
        long start = System.nanoTime();
        do {
            try {
                Thread.sleep(1L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        } while (System.nanoTime() - start < PAUSE_NANOS);
    }

    static void assertTicks(GameTestHelper helper, String label, Entity entity) {
        helper.assertTrue(helper.getLevel().isPositionEntityTicking(entity.blockPosition()),
                "The " + label + "'s chunk does not tick entities (" + label + " at " + entity.blockPosition()
                        + ", origin " + helper.absolutePos(BlockPos.ZERO) + ")");
    }
}
