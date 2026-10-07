package rotp.core.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import rotp.core.core.JojoMod;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** {@link GameTestChunkMargin}: the chunks around a test cell are ready when the test starts. */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GameTestChunkMarginGameTests {
	private GameTestChunkMarginGameTests() {}

	// a batch of its own: no other running test loads the chunks around this cell
	@GameTest(template = "empty", batch = "gametest_chunk_margin", timeoutTicks = 40)
	public static void actorsInTheChunksAroundTheCellAreFoundAndTicked(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos cell = helper.absolutePos(BlockPos.ZERO);
		ChunkPos cellChunk = new ChunkPos(cell);
		Map<ChunkPos, Pig> pigs = new LinkedHashMap<>();
		boolean scheduled = false;
		try {
			List<String> hidden = new ArrayList<>();
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					ChunkPos chunk = new ChunkPos(cellChunk.x + dx, cellChunk.z + dz);
					Pig pig = EntityType.PIG.create(level);
					helper.assertTrue(pig != null, "Could not create a pig");
					pig.setNoAi(true);
					pig.moveTo(chunk.getMiddleBlockX() + 0.5D, cell.getY() + 8.0D, chunk.getMiddleBlockZ() + 0.5D, 0.0F, 0.0F);
					helper.assertTrue(level.addFreshEntity(pig), "Could not add the pig of chunk " + chunk);
					pigs.put(chunk, pig);
					if (!level.getEntitiesOfClass(Pig.class, new AABB(pig.blockPosition())).contains(pig)) {
						hidden.add(relative(cellChunk, chunk));
					}
				}
			}
			helper.assertTrue(hidden.isEmpty(), "An entity added around the test cell is not found in the chunks " + hidden);
			scheduled = true;
			helper.runAfterDelay(5, () -> {
				try {
					List<String> frozen = new ArrayList<>();
					pigs.forEach((chunk, pig) -> {
						if (pig.tickCount < 4) {
							frozen.add(relative(cellChunk, chunk) + " ticks=" + pig.tickCount);
						}
					});
					helper.assertTrue(frozen.isEmpty(), "An entity around the test cell was not ticked for 5 ticks in the chunks "
							+ frozen);
				}
				finally {
					pigs.values().forEach(Pig::discard);
				}
				helper.succeed();
			});
		}
		finally {
			if (!scheduled) {
				pigs.values().forEach(Pig::discard);
			}
		}
	}

	private static String relative(ChunkPos cellChunk, ChunkPos chunk) {
		return "(" + (chunk.x - cellChunk.x) + "," + (chunk.z - cellChunk.z) + ")";
	}
}
