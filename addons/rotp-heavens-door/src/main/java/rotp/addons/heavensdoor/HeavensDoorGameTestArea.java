package rotp.addons.heavensdoor;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * The GameTest runner forces only the chunks of a test's own 1x1x1 structure. A fixture that reaches into the next
 * chunk found it, depending on where the batch placed the test, not yet loaded (entities spawned there are hidden
 * from entity searches) or loaded without entity ticking (a Stand there never ticks its action). The fixture's chunks
 * are forced too, and the test body runs once all of them tick entities; the runner unforces every chunk after the
 * batch.
 */
final class HeavensDoorGameTestArea {
    // relative corners that hold every fixture position of the Heaven's Door tests
    static final Vec3 MIN = new Vec3(-2.0D, 2.0D, -1.0D);
    static final Vec3 MAX = new Vec3(8.0D, 2.0D, 8.0D);
    // The game test server runs its ticks back to back, so the wait has a time budget too: every waiting tick lasts
    // at least a millisecond, and the wait fails only once both budgets are used up.
    private static final int WAIT_TICKS = 4000;
    private static final long WAIT_NANOS = 30_000_000_000L;
    /** The most ticks the wait can take; a test adds it to its own timeout. */
    static final int WAIT_TEST_TICKS = 30_100;

    private HeavensDoorGameTestArea() {}

    static void whenLoaded(GameTestHelper helper, Runnable body) {
        ServerLevel level = helper.getLevel();
        BlockPos min = BlockPos.containing(helper.absoluteVec(MIN));
        BlockPos max = BlockPos.containing(helper.absoluteVec(MAX));
        List<ChunkPos> chunks = new ArrayList<>();
        for (int x = Math.min(min.getX(), max.getX()) >> 4; x <= Math.max(min.getX(), max.getX()) >> 4; x++) {
            for (int z = Math.min(min.getZ(), max.getZ()) >> 4; z <= Math.max(min.getZ(), max.getZ()) >> 4; z++) {
                level.setChunkForced(x, z, true);
                chunks.add(new ChunkPos(x, z));
            }
        }
        await(helper, chunks, 0, System.nanoTime(), body);
    }

    private static void await(GameTestHelper helper, List<ChunkPos> chunks, int waitedTicks, long startNanos,
            Runnable body) {
        ChunkPos pending = null;
        for (ChunkPos chunk : chunks) {
            if (!helper.getLevel().isPositionEntityTicking(chunk.getWorldPosition())) {
                pending = chunk;
                break;
            }
        }
        if (pending == null) {
            body.run();
            return;
        }
        long waitedNanos = System.nanoTime() - startNanos;
        helper.assertTrue(waitedTicks < WAIT_TICKS || waitedNanos < WAIT_NANOS,
                "fixture chunk " + pending + " does not tick entities yet (waited " + waitedTicks + " ticks, "
                        + waitedNanos / 1_000_000L + " ms)");
        pause();
        helper.runAfterDelay(1, () -> await(helper, chunks, waitedTicks + 1, startNanos, body));
    }

    // Thread.sleep, not LockSupport.parkNanos: a pending unpark permit of the server thread ends a park at once.
    private static void pause() {
        long start = System.nanoTime();
        do {
            try {
                Thread.sleep(1L);
            }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        } while (System.nanoTime() - start < 1_000_000L);
    }
}
