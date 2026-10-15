package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.ChunkPos;

/**
 * Light is computed on a thread of its own and the game test server does not pace its ticks: under load that thread
 * can run many ticks behind, so a block placed by a fixture is read back with the light of what stood there before.
 * A fixture that reads the shade of a roof it has just placed waits here until the light engine has applied every
 * change queued so far. The wait is measured in real time, not in ticks.
 */
final class GameTestLight {
	static final long BUDGET_NANOS = TimeUnit.SECONDS.toNanos(20L);
	private static final long PAUSE_NANOS = TimeUnit.MILLISECONDS.toNanos(5L);

	private GameTestLight() {}

	/** Completes on the light thread after every light change queued so far for the nine chunks around the chunk. */
	static CompletableFuture<?> pending(ServerLevel level, ChunkPos chunk) {
		ThreadedLevelLightEngine light = level.getChunkSource().getLightEngine();
		List<CompletableFuture<?>> pending = new ArrayList<>();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				pending.add(light.waitForPendingTasks(chunk.x + dx, chunk.z + dz));
			}
		}
		return CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new));
	}

	/** One wait for the light of a chunk: ask {@link #stalled()} on every poll the fixture spends waiting. */
	static final class Wait {
		private final CompletableFuture<?> pending;
		private final long startedAt = System.nanoTime();

		Wait(ServerLevel level, ChunkPos chunk) {
			this.pending = pending(level, chunk);
		}

		boolean done() {
			return pending.isDone();
		}

		/** Gives the light thread a moment and tells whether the real-time budget of the wait is spent. */
		boolean stalled() {
			LockSupport.parkNanos(PAUSE_NANOS);
			return System.nanoTime() - startedAt > BUDGET_NANOS;
		}

		long millis() {
			return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
		}
	}
}
