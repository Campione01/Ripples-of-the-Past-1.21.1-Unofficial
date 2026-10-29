package rotp.core.gametest;

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
 * Chunks a game test fixture holds itself, for a scene that stands away from its test cell (the pattern of the
 * add-ons' PlanetWavesChunkTickets and GameTestChunkTickets). The framework, with {@link GameTestChunkMargin}, holds
 * the chunks within 16 blocks of each cell while the cell's batch runs; past them a chunk is held only by chance, an
 * entity added to a chunk nobody holds is not found, and an entity ticks only in a chunk with a ticking ticket (the
 * game test server's simulation distance is 0, and a mock player holds no chunk). The fixture holds every chunk within
 * MARGIN blocks of the places its entities use at the entity-ticking level, with tickets of its own, and returns once
 * they all tick entities. A newly held chunk gets there only after the ring around it has generated, which takes real
 * time, while the server does not pace its ticks: the wait is real time, the server thread runs the chunk tasks
 * meanwhile and pauses when there is none, and no game tick passes. The tickets go when the test ends, however it ends.
 */
final class GameTestChunkTickets implements GameTestListener {
	static final double MARGIN = 3.0D;
	// a region ticket of radius 2 has level 31, the entity-ticking level of a forced chunk
	private static final int TICKET_RADIUS = 2;
	private static final TicketType<UUID> TICKET = TicketType.create("rotp_gametest_fixture", Comparator.<UUID>naturalOrder());
	private static final long WAIT_NANOS = 30_000_000_000L;
	private static final long PAUSE_NANOS = 1_000_000L;

	private final ServerLevel level;
	private final UUID id = UUID.randomUUID();
	private final Set<ChunkPos> held = new LinkedHashSet<>();

	GameTestChunkTickets(GameTestHelper helper) {
		level = helper.getLevel();
		helper.testInfo.addListener(this);
	}

	/** Every chunk within MARGIN blocks of the box between the two corners; returns once they all tick entities. */
	GameTestChunkTickets hold(Vec3 corner, Vec3 otherCorner) {
		int minX = SectionPos.blockToSectionCoord(Mth.floor(Math.min(corner.x, otherCorner.x) - MARGIN));
		int maxX = SectionPos.blockToSectionCoord(Mth.floor(Math.max(corner.x, otherCorner.x) + MARGIN));
		int minZ = SectionPos.blockToSectionCoord(Mth.floor(Math.min(corner.z, otherCorner.z) - MARGIN));
		int maxZ = SectionPos.blockToSectionCoord(Mth.floor(Math.max(corner.z, otherCorner.z) + MARGIN));
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				add(new ChunkPos(x, z));
			}
		}
		await();
		return this;
	}

	/** That chunk alone, as the framework holds a one-block cell's; returns once it ticks entities. */
	GameTestChunkTickets holdChunk(ChunkPos chunk) {
		add(chunk);
		await();
		return this;
	}

	private void add(ChunkPos chunk) {
		if (held.add(chunk)) {
			level.getChunkSource().addRegionTicket(TICKET, chunk, TICKET_RADIUS, id);
		}
	}

	List<ChunkPos> notTickingEntities() {
		List<ChunkPos> idle = new ArrayList<>();
		for (ChunkPos chunk : held) {
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
		for (ChunkPos chunk : held) {
			level.getChunkSource().removeRegionTicket(TICKET, chunk, TICKET_RADIUS, id);
		}
		held.clear();
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
