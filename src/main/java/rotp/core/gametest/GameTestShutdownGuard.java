package rotp.core.gametest;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import com.mojang.logging.LogUtils;

import rotp.core.core.JojoMod;

import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

/**
 * Vanilla 1.21.1 can spin for ever in MinecraftServer.stopServer: ChunkMap.scheduleUnload re-queues an unload at
 * once while a generation task claims its chunk holder, and the stop loop drains that queue with unlimited time, so
 * a generation step that waits for the server thread never runs. A game test server halts in the tick its last test
 * finishes, with chunk generation still in flight. Before such a server stops, chunk generation is given the server
 * thread until nothing claims a chunk holder any more. Add-on game test servers load this mod and are covered too.
 */
@EventBusSubscriber(modid = JojoMod.MOD_ID)
public final class GameTestShutdownGuard {
	private static final Logger LOGGER = LogUtils.getLogger();
	static final long BUDGET_NANOS = TimeUnit.SECONDS.toNanos(60L);
	private static final long PAUSE_NANOS = TimeUnit.MILLISECONDS.toNanos(1L);
	private static boolean registered;

	private GameTestShutdownGuard() {}

	/** What one {@link #settle} call found and did. */
	record Settled(boolean settled, long nanos, int chunkTasks, int claimedAtStart, int holdersAtStart,
			int claimedAtEnd, int pendingAtEnd) {}

	@SubscribeEvent
	public static void onServerStarting(ServerStartingEvent event) {
		if (event.getServer() instanceof GameTestServer && !registered) {
			registered = true;
			// last, so that nothing another stop listener does starts chunk work behind the guard
			NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, ServerStoppingEvent.class,
					GameTestShutdownGuard::onGameTestServerStopping);
		}
	}

	private static void onGameTestServerStopping(ServerStoppingEvent event) {
		MinecraftServer server = event.getServer();
		long deadline = System.nanoTime() + BUDGET_NANOS;
		for (ServerLevel level : server.getAllLevels()) {
			String dimension = level.dimension().location().toString();
			try {
				Settled result = settle(level, Math.max(0L, deadline - System.nanoTime()));
				if (result.settled()) {
					LOGGER.info("Game test shutdown guard: chunk generation of {} settled in {} ms after {} "
									+ "server-thread chunk tasks; {} of {} chunk holders were claimed by generation "
									+ "when the server halted",
							dimension, TimeUnit.NANOSECONDS.toMillis(result.nanos()), result.chunkTasks(),
							result.claimedAtStart(), result.holdersAtStart());
				}
				else {
					LOGGER.error("GAME TEST SHUTDOWN GUARD: chunk generation of {} did not settle within {} ms: {} "
									+ "chunk holders are still claimed by generation tasks and {} server-thread "
									+ "chunk tasks are pending after {} tasks. The server stops in this state and "
									+ "can spin for ever in ChunkMap.processUnloads",
							dimension, TimeUnit.NANOSECONDS.toMillis(result.nanos()), result.claimedAtEnd(),
							result.pendingAtEnd(), result.chunkTasks());
				}
			}
			catch (RuntimeException exception) {
				LOGGER.error("GAME TEST SHUTDOWN GUARD: could not settle chunk generation of {}. The server stops "
						+ "unguarded and can spin for ever in ChunkMap.processUnloads", dimension, exception);
			}
		}
	}

	/**
	 * Runs the level's server-thread chunk tasks, with short pauses of real time for the chunk workers, until no chunk
	 * holder is claimed by a generation task and no chunk task is pending, or until the budget is used up.
	 */
	static Settled settle(ServerLevel level, long budgetNanos) {
		ServerChunkCache chunks = level.getChunkSource();
		Map<?, ?> holders = chunkHolders(chunks);
		int claimedAtStart = claimedByGeneration(holders);
		int holdersAtStart = holders.size();
		long startedAt = System.nanoTime();
		int chunkTasks = 0;
		while (true) {
			if (chunks.pollTask()) {
				chunkTasks++;
			}
			else {
				// a claim is only ever taken on this thread, so none appears behind this check; a worker may
				// still post a chunk task at any time, which is why the counts are reported as they were seen here
				int claimed = claimedByGeneration(holders);
				int pending = chunks.getPendingTasksCount();
				if (claimed == 0 && pending == 0) {
					return new Settled(true, System.nanoTime() - startedAt, chunkTasks, claimedAtStart,
							holdersAtStart, 0, 0);
				}
				// the game test server does not pace its ticks: the workers get real time here
				LockSupport.parkNanos(PAUSE_NANOS);
			}
			if (System.nanoTime() - startedAt > budgetNanos) {
				return new Settled(false, System.nanoTime() - startedAt, chunkTasks, claimedAtStart, holdersAtStart,
						claimedByGeneration(holders), chunks.getPendingTasksCount());
			}
		}
	}

	static int claimedByGeneration(ServerLevel level) {
		return claimedByGeneration(chunkHolders(level.getChunkSource()));
	}

	private static Map<?, ?> chunkHolders(ServerChunkCache chunks) {
		try {
			// the map generation tasks claim their holders from; ChunkMap has no public view of it
			Field updating = ChunkMap.class.getDeclaredField("updatingChunkMap");
			updating.setAccessible(true);
			return (Map<?, ?>) updating.get(chunks.chunkMap);
		}
		catch (ReflectiveOperationException | RuntimeException exception) {
			throw new IllegalStateException("Cannot read ChunkMap.updatingChunkMap: the shutdown guard cannot tell "
					+ "whether chunk generation is still in flight", exception);
		}
	}

	private static int claimedByGeneration(Map<?, ?> holders) {
		int claimed = 0;
		for (Object holder : holders.values()) {
			if (((GenerationChunkHolder) holder).getGenerationRefCount() != 0) {
				claimed++;
			}
		}
		return claimed;
	}
}
