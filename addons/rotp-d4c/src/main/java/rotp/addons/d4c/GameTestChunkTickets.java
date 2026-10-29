package rotp.addons.d4c;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.LongStream;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * Chunk tickets owned by one GameTest fixture. The framework forces only the chunk of the one-block template, with a
 * flag that every test shares and the runner clears between batches, and its grid puts the template on any block of a
 * chunk. A fixture therefore holds tickets of its own for every chunk within MARGIN_BLOCKS of each position its
 * entities use, and waits until they tick entities before it relies on them. The server ticks as fast as it can, so
 * the wait is paced and budgeted in real time: a budget counted in ticks races the chunk workers.
 */
final class GameTestChunkTickets {
    static final int MARGIN_BLOCKS = 3;
    /** How far from the template origin, in blocks, the entities of a fixture stand. */
    static final int FIXTURE_REACH = 8;
    static final long WAIT_BUDGET_MILLIS = 60_000L;
    static final long PAUSE_MILLIS = 5L;
    /** What a test adds to its timeoutTicks: a waiting tick lasts at least PAUSE_MILLIS, so the budget ends before it. */
    static final int WAIT_TICKS = (int) (WAIT_BUDGET_MILLIS / PAUSE_MILLIS) + 200;

    private static final TicketType<UUID> TICKET = TicketType.create("rotp_d4c_gametest", Comparator.<UUID>naturalOrder());
    // Level 31 is the entity-ticking level, the one a forced chunk gets.
    private static final int TICKET_RADIUS = 2;
    private static long pausedAt = Long.MIN_VALUE;

    private final GameTestHelper helper;
    private final ServerLevel level;
    private final UUID id = UUID.randomUUID();
    private final Set<ChunkPos> held = new LinkedHashSet<>();
    private boolean released;

    GameTestChunkTickets(GameTestHelper helper) {
        this(helper, helper.getLevel(), false);
    }

    GameTestChunkTickets(GameTestHelper helper, ServerLevel level) {
        this(helper, level, false);
    }

    private GameTestChunkTickets(GameTestHelper helper, ServerLevel level, boolean ofTest) {
        this.helper = helper;
        this.level = level;
        helper.testInfo.addListener(new Releaser(this, ofTest));
    }

    /** Releases the tickets when the test ends, however it ends. */
    private record Releaser(GameTestChunkTickets tickets, boolean ofTest) implements GameTestListener {
        @Override
        public void testStructureLoaded(GameTestInfo test) {}

        @Override
        public void testPassed(GameTestInfo test, GameTestRunner runner) {
            tickets.release();
        }

        @Override
        public void testFailed(GameTestInfo test, GameTestRunner runner) {
            tickets.release();
        }

        @Override
        public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {
            tickets.release();
        }
    }

    /** The tickets of the test as a whole, for fixtures that are the test. */
    static GameTestChunkTickets forTest(GameTestHelper helper) {
        return helper.testInfo.getListeners()
                .filter(listener -> listener instanceof Releaser releaser && releaser.ofTest())
                .map(listener -> ((Releaser) listener).tickets())
                .findFirst()
                .orElseGet(() -> new GameTestChunkTickets(helper, helper.getLevel(), true));
    }

    /** The tickets of the test, covering FIXTURE_REACH blocks around the template origin. */
    static GameTestChunkTickets aroundOrigin(GameTestHelper helper) {
        return forTest(helper).coverAround(helper.absolutePos(BlockPos.ZERO), FIXTURE_REACH);
    }

    GameTestChunkTickets cover(BlockPos pos) {
        return cover(pos.getX() + 0.5D, pos.getZ() + 0.5D);
    }

    GameTestChunkTickets cover(Vec3 pos) {
        return cover(pos.x, pos.z);
    }

    GameTestChunkTickets cover(double x, double z) {
        return coverBox(x, z, x, z);
    }

    /** Every chunk within MARGIN_BLOCKS of the box between the two corners. */
    GameTestChunkTickets coverBox(double minX, double minZ, double maxX, double maxZ) {
        int fromX = Mth.floor(Math.min(minX, maxX) - MARGIN_BLOCKS) >> 4;
        int toX = Mth.floor(Math.max(minX, maxX) + MARGIN_BLOCKS) >> 4;
        int fromZ = Mth.floor(Math.min(minZ, maxZ) - MARGIN_BLOCKS) >> 4;
        int toZ = Mth.floor(Math.max(minZ, maxZ) + MARGIN_BLOCKS) >> 4;
        for (int x = fromX; x <= toX; x++) {
            for (int z = fromZ; z <= toZ; z++) {
                ChunkPos chunk = new ChunkPos(x, z);
                if (!released && held.add(chunk)) {
                    level.getChunkSource().addRegionTicket(TICKET, chunk, TICKET_RADIUS, id);
                }
            }
        }
        return this;
    }

    /** Every chunk within MARGIN_BLOCKS of a square of the given reach around the block. */
    GameTestChunkTickets coverAround(BlockPos centre, int reach) {
        return coverBox(centre.getX() - reach, centre.getZ() - reach, centre.getX() + reach + 1, centre.getZ() + reach + 1);
    }

    ChunkPos firstNotTicking() {
        for (ChunkPos chunk : held) {
            if (!level.isPositionEntityTicking(chunk.getWorldPosition())) {
                return chunk;
            }
        }
        return null;
    }

    boolean ready() {
        return firstNotTicking() == null;
    }

    /** Runs the body in this tick if the chunks tick entities, else in the first tick that finds them ticking. */
    void whenTicking(Runnable onTimeout, Runnable body) {
        whenTicking(onTimeout, later -> body.run());
    }

    /**
     * As above; a body that had to wait gets a helper whose per-tick tasks start with the next tick, as the tick it
     * runs in is already past the framework's scheduled tasks (a task for the tick itself would run twice in the next).
     */
    void whenTicking(Runnable onTimeout, Consumer<GameTestHelper> body) {
        if (ready()) {
            OwnWork.of(helper).starts(helper.getTick());
            body.accept(helper);
            return;
        }
        helper.startSequence().thenWaitUntil(awaiting(onTimeout)).thenExecute(() -> {
            OwnWork.of(helper).starts(helper.getTick());
            try {
                body.accept(new LaterHelper(helper.testInfo));
            }
            catch (GameTestAssertException failure) {
                throw failure;
            }
            catch (RuntimeException failure) {
                helper.testInfo.fail(failure);
            }
        });
    }

    /** Runs the body every tick, from the first tick after the one in which the chunks tick entities. */
    void onEachTickWhenTicking(Runnable onTimeout, Runnable body) {
        helper.startSequence().thenWaitUntil(awaiting(onTimeout)).thenExecute(() -> {
            OwnWork work = OwnWork.of(helper);
            work.waited(1L);
            work.starts(helper.getTick() + 1L);
            new LaterHelper(helper.testInfo).onEachTick(body);
        });
    }

    /**
     * A wrapped test's timeoutTicks is its own timeout plus WAIT_TICKS. Its own work keeps the own timeout, counted from
     * the tick its first body starts; a later wait for more chunks, and the tick a later per-tick body is put off by,
     * are not counted.
     */
    private static final class OwnWork implements GameTestListener {
        private final GameTestHelper helper;
        private long deadline = -1L;
        private long waitedTicks;
        private int waiting;

        private OwnWork(GameTestHelper helper) {
            this.helper = helper;
        }

        static OwnWork of(GameTestHelper helper) {
            return helper.testInfo.getListeners()
                    .filter(OwnWork.class::isInstance)
                    .map(OwnWork.class::cast)
                    .findFirst()
                    .orElseGet(() -> {
                        OwnWork work = new OwnWork(helper);
                        helper.testInfo.addListener(work);
                        return work;
                    });
        }

        // A sequence, not a scheduled task: this may run inside the framework's loop over its scheduled tasks.
        void starts(long tick) {
            int ownTicks = helper.testInfo.getTimeoutTicks() - WAIT_TICKS;
            if (deadline >= 0L || ownTicks <= 0) {
                return;
            }
            deadline = tick + ownTicks;
            GameTestAssertException pending = new GameTestAssertException(
                    "The test's own " + ownTicks + " ticks from tick " + tick + " have not run out.");
            helper.startSequence().thenWaitUntil(() -> {
                if (waiting > 0 || helper.getTick() <= deadline) {
                    throw pending;
                }
            }).thenExecute(() -> helper.fail("Didn't succeed or fail within its own " + ownTicks
                    + " ticks: its work started at tick " + tick + ", and " + waitedTicks
                    + " later ticks of chunk waits were not counted."));
        }

        void waitStarts() {
            waiting++;
        }

        void waitEnds(long ticks) {
            waiting--;
            waited(ticks);
        }

        void waited(long ticks) {
            if (deadline >= 0L) {
                deadline += ticks;
                waitedTicks += ticks;
            }
        }

        @Override
        public void testStructureLoaded(GameTestInfo test) {}

        @Override
        public void testPassed(GameTestInfo test, GameTestRunner runner) {}

        @Override
        public void testFailed(GameTestInfo test, GameTestRunner runner) {}

        @Override
        public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {}
    }

    private static final class LaterHelper extends GameTestHelper {
        private final GameTestInfo info;

        private LaterHelper(GameTestInfo info) {
            super(info);
            this.info = info;
        }

        @Override
        public void onEachTick(Runnable task) {
            LongStream.range(getTick() + 1L, info.getTimeoutTicks())
                    .forEach(tick -> info.setRunAtTickTime(tick, task::run));
        }
    }

    // A sequence runs outside the framework's loop over its scheduled tasks, which a task must not add to.
    private Runnable awaiting(Runnable onTimeout) {
        long deadline = System.nanoTime() + WAIT_BUDGET_MILLIS * 1_000_000L;
        OwnWork work = OwnWork.of(helper);
        long[] waitingSince = {-1L};
        return () -> {
            ChunkPos idle = firstNotTicking();
            if (idle == null) {
                if (waitingSince[0] >= 0L) {
                    work.waitEnds(helper.getTick() - waitingSince[0]);
                    waitingSince[0] = -1L;
                }
                return;
            }
            if (waitingSince[0] < 0L) {
                waitingSince[0] = helper.getTick();
                work.waitStarts();
            }
            if (System.nanoTime() - deadline > 0L) {
                onTimeout.run();
                GameTestAssertException failure = new GameTestAssertException("Chunk " + idle + " of this fixture's "
                        + held.size() + " ticketed chunks still does not tick entities after " + WAIT_BUDGET_MILLIS
                        + " ms.");
                helper.testInfo.fail(failure);
                throw failure;
            }
            pauseOncePerTick();
            throw new GameTestAssertException("Chunk " + idle + " does not tick entities yet.");
        };
    }

    // Once per game tick however many fixtures wait, so that the pause stays PAUSE_MILLIS a tick.
    private void pauseOncePerTick() {
        long now = level.getGameTime();
        if (now == pausedAt) {
            return;
        }
        pausedAt = now;
        try {
            Thread.sleep(PAUSE_MILLIS);
        }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    void release() {
        if (released) {
            return;
        }
        released = true;
        for (ChunkPos chunk : held) {
            level.getChunkSource().removeRegionTicket(TICKET, chunk, TICKET_RADIUS, id);
        }
        held.clear();
    }
}
