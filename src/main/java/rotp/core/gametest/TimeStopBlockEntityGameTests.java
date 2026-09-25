package rotp.core.gametest;

import java.lang.reflect.Field;
import java.util.Map;

import rotp.core.core.JojoMod;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.subsystems.timestop.TimeStopState;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Block entities in a stopped chunk do not tick. 1.16 ServerChunkProviderMixin made isTickingChunk false there, and
 * World.tickBlockEntities skipped such block entities. The test runs the furnace's own chunk ticker by hand, so no
 * level tick (and no stop lifecycle tick) runs in between.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TimeStopBlockEntityGameTests {
	private static int nextStopId = -7_320_000;

	private TimeStopBlockEntityGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40, batch = GameTestBatches.TIME_STOP)
	public static void furnaceInStoppedChunkDoesNotCook(GameTestHelper helper) {
		BlockPos relative = new BlockPos(1, 1, 1);
		BlockPos pos = helper.absolutePos(relative);
		TimeStopState state = helper.getLevel().getData(ModDataAttachmentTypes.TIME_STOP.get());
		int stop = nextStopId--;
		try {
			helper.setBlock(relative, Blocks.FURNACE);
			FurnaceBlockEntity furnace = helper.getBlockEntity(relative);
			furnace.setItem(0, new ItemStack(Items.BEEF, 4));
			furnace.setItem(1, new ItemStack(Items.COAL, 2));

			tickFurnace(helper, pos);
			int cook = cookTime(helper, furnace);
			int burn = burnTime(helper, furnace);
			helper.assertTrue(cook == 1 && burn > 0, "The furnace did not light: cook " + cook + ", burn " + burn);

			helper.assertTrue(state.tryPutInstance(new TimeStopState.Instance(stop, 200, 200,
					new ChunkPos(pos), 2, -1, "block_entity_test")), "Could not stop time");
			helper.assertTrue(state.isTimeStopped(new ChunkPos(pos)), "The furnace's chunk is not stopped");
			for (int tick = 0; tick < 40; tick++) {
				tickFurnace(helper, pos);
			}
			helper.assertTrue(cookTime(helper, furnace) == cook && burnTime(helper, furnace) == burn,
					"A furnace in stopped time kept cooking: cook " + cookTime(helper, furnace)
					+ " (was " + cook + "), burn " + burnTime(helper, furnace) + " (was " + burn + ")");

			state.removeInstance(stop);
			tickFurnace(helper, pos);
			helper.assertTrue(cookTime(helper, furnace) == cook + 1,
					"The furnace did not carry on after time resumed: cook " + cookTime(helper, furnace));
		}
		finally {
			state.removeInstance(stop);
			if (helper.getLevel().getBlockEntity(pos) instanceof FurnaceBlockEntity left) {
				left.clearContent();
			}
			helper.setBlock(relative, Blocks.AIR);
		}
		helper.succeed();
	}

	// the wrapper Level.tickBlockEntities calls for this position
	private static void tickFurnace(GameTestHelper helper, BlockPos pos) {
		LevelChunk chunk = helper.getLevel().getChunkAt(pos);
		Object ticker = tickers(chunk).get(pos);
		helper.assertTrue(ticker instanceof TickingBlockEntity, "The furnace has no ticker");
		((TickingBlockEntity) ticker).tick();
	}

	private static Map<?, ?> tickers(LevelChunk chunk) {
		try {
			Field field = LevelChunk.class.getDeclaredField("tickersInLevel");
			field.setAccessible(true);
			return (Map<?, ?>) field.get(chunk);
		}
		catch (ReflectiveOperationException error) {
			throw new AssertionError("Could not read the chunk's block entity tickers", error);
		}
	}

	private static int cookTime(GameTestHelper helper, FurnaceBlockEntity furnace) {
		return saved(helper, furnace).getInt("CookTime");
	}

	private static int burnTime(GameTestHelper helper, FurnaceBlockEntity furnace) {
		return saved(helper, furnace).getInt("BurnTime");
	}

	private static CompoundTag saved(GameTestHelper helper, FurnaceBlockEntity furnace) {
		return furnace.saveWithoutMetadata(helper.getLevel().registryAccess());
	}
}
