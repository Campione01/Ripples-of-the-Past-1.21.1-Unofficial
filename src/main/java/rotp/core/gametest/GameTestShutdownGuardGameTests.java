package rotp.core.gametest;

import java.util.Comparator;

import rotp.core.core.JojoMod;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** {@link GameTestShutdownGuard}: chunk generation in flight is finished before a game test server may stop. */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GameTestShutdownGuardGameTests {
	private static final TicketType<ChunkPos> FIXTURE_TICKET =
			TicketType.create("rotp_gametest_shutdown_guard", Comparator.comparingLong(ChunkPos::toLong));
	// far outside the test grid and the chunk margins of its cells
	private static final int FIXTURE_CHUNK_DISTANCE = 40;

	private GameTestShutdownGuardGameTests() {}

	// a batch of its own: settling runs every chunk task of the level at once, which no other test should feel
	@GameTest(template = "empty", batch = "gametest_shutdown_guard")
	public static void settleFinishesChunkGenerationThatWaitsForTheServerThread(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerChunkCache chunks = level.getChunkSource();
		ChunkPos cell = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
		ChunkPos far = new ChunkPos(cell.x + (cell.x > 0 ? -FIXTURE_CHUNK_DISTANCE : FIXTURE_CHUNK_DISTANCE), cell.z);
		helper.assertTrue(chunks.getChunkNow(far.x, far.z) == null,
				"Fixture: the chunk " + far + " is already loaded, it cannot show generation in flight");
		chunks.addRegionTicket(FIXTURE_TICKET, far, 1, far);
		try {
			// the first chunk task applies the ticket and starts the generation tasks
			chunks.pollTask();
			int claimed = GameTestShutdownGuard.claimedByGeneration(level);
			helper.assertTrue(claimed > 0 && chunks.getChunkNow(far.x, far.z) == null,
					"Fixture: the ticket on " + far + " did not leave chunk generation in flight: claimed=" + claimed
							+ " loaded=" + (chunks.getChunkNow(far.x, far.z) != null));

			GameTestShutdownGuard.Settled result = GameTestShutdownGuard.settle(level, GameTestShutdownGuard.BUDGET_NANOS);

			JojoMod.LOGGER.info("Game test shutdown guard fixture: claimedBefore={} {}", claimed, result);
			int claimedAfter = GameTestShutdownGuard.claimedByGeneration(level);
			boolean loaded = chunks.getChunkNow(far.x, far.z) != null;
			// The pending count is not read again here: a chunk worker may post a task right after the guard
			// saw none.
			helper.assertTrue(result.settled() && claimedAfter == 0 && loaded,
					"The shutdown guard left chunk generation in flight: settled=" + result.settled()
							+ " claimedBefore=" + claimed + " claimedAfter=" + claimedAfter
							+ " chunkTasksRun=" + result.chunkTasks() + " requestedChunkLoaded=" + loaded
							+ ", expected settled=true claimedAfter=0 requestedChunkLoaded=true");
			helper.assertTrue(result.chunkTasks() > 0 && result.claimedAtEnd() == 0 && result.pendingAtEnd() == 0,
					"The guard's own report does not match what it did: " + result);
		}
		finally {
			chunks.removeRegionTicket(FIXTURE_TICKET, far, 1, far);
		}
		helper.succeed();
	}
}
