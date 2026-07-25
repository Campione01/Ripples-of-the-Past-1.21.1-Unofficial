package com.github.standobyte.jojoimpl.stands.goldexperience;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.ModDataAttachmentTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = JojoMod.MOD_ID)
public class GETreeLeavesDecayState {
	private final ServerLevel level;
	private final List<TreeDecay> decayingTrees = new LinkedList<>();

	public GETreeLeavesDecayState(ServerLevel level) {
		this.level = level;
	}

	@Nullable
	public static TreeDecay startDecay(ServerLevel level, BlockPos blockPos, int duration, int leavesPerTick) {
		TreeDecay tree = TreeDecay.createFromLogBlock(blockPos, level);
		if (tree != null && tree.isValid()) {
			GETreeLeavesDecayState state = level.getData(ModDataAttachmentTypes.GE_TREE_DECAY.get());
			state.decayingTrees.add(tree);
			tree.updateDecay(duration, leavesPerTick);
			return tree;
		}
		return null;
	}

	public static boolean isTreeStemBlock(BlockState state) {
		return state.is(BlockTags.LOGS) || state.is(Blocks.MUSHROOM_STEM);
	}

	private void tick() {
		Iterator<TreeDecay> iter = decayingTrees.iterator();
		while (iter.hasNext()) {
			TreeDecay tree = iter.next();
			if (tree.tick(level)) {
				iter.remove();
			}
		}
	}

	@SubscribeEvent
	public static void onLevelTick(LevelTickEvent.Post event) {
		if (event.getLevel() instanceof ServerLevel level) {
			var attachmentType = ModDataAttachmentTypes.GE_TREE_DECAY.get();
			if (level.hasData(attachmentType)) {
				level.getData(attachmentType).tick();
			}
		}
	}

	public static class TreeDecay {
		private static final int RANGE = 16;

		private final Set<BlockPos> logs = new HashSet<>();
		private List<Set<BlockPos>> leaves = new ArrayList<>();
		private Block logType;
		private Block leavesType;
		private int decayTicks;
		private int decayPerTick;

		public Set<BlockPos> logs() {
			return logs;
		}

		@Nullable
		private static TreeDecay createFromLogBlock(BlockPos pos, ServerLevel level) {
			BlockState state = level.getBlockState(pos);
			if (isTreeStemBlock(state)) {
				TreeDecay tree = new TreeDecay();
				tree.logType = state.getBlock();
				CubeBoolArrUtil checkedTable = new CubeBoolArrUtil(RANGE * 2 + 1);
				tree.recAddTreeBlock(pos, Vec3i.ZERO, level, checkedTable);
				Collections.reverse(tree.leaves);
				return tree;
			}
			return null;
		}

		private void recAddTreeBlock(BlockPos originalPos, Vec3i offset, ServerLevel level, CubeBoolArrUtil checkedTable) {
			int distance = Math.abs(offset.getX()) + Math.abs(offset.getY()) + Math.abs(offset.getZ());
			if (distance > RANGE) {
				return;
			}

			int iX = offset.getX() + RANGE;
			int iY = offset.getY() + RANGE;
			int iZ = offset.getZ() + RANGE;
			if (checkedTable.get(iX, iY, iZ)) {
				return;
			}
			checkedTable.set(iX, iY, iZ, true);

			boolean validBlock = false;
			BlockPos pos = originalPos.offset(offset);
			Block block = level.getBlockState(pos).getBlock();

			if (logType == block) {
				validBlock = true;
				logs.add(pos);
			}
			else {
				if (leavesType != null) {
					validBlock = leavesType == block;
				}
				else if (block instanceof LeavesBlock || block instanceof HugeMushroomBlock) {
					leavesType = block;
					validBlock = true;
				}
				if (validBlock) {
					for (int i = leaves.size(); i < distance; ++i) {
						leaves.add(new HashSet<>());
					}
					leaves.get(distance - 1).add(pos);
				}
			}

			if (!validBlock || distance >= RANGE) {
				return;
			}

			for (Direction direction : Direction.values()) {
				Vec3i nextOffset = new Vec3i(
						offset.getX() + direction.getStepX(),
						offset.getY() + direction.getStepY(),
						offset.getZ() + direction.getStepZ());
				recAddTreeBlock(originalPos, nextOffset, level, checkedTable);
				if (direction.getAxis() != Direction.Axis.Y) {
					recAddTreeBlock(originalPos, new Vec3i(
							offset.getX() + direction.getStepX(),
							offset.getY() + direction.getStepY() - 1,
							offset.getZ() + direction.getStepZ()), level, checkedTable);
				}
			}
		}

		private boolean isValid() {
			return leavesType != null;
		}

		private void updateDecay(int ticks, int leavesPerTick) {
			decayPerTick = Math.max(leavesPerTick, decayPerTick);
			decayTicks = Math.max(ticks, decayTicks);
		}

		private boolean tick(ServerLevel level) {
			if (decayTicks <= 0) {
				return true;
			}
			--decayTicks;

			int leavesRemoved = 0;
			Iterator<Set<BlockPos>> iter = leaves.iterator();
			while (leavesRemoved < decayPerTick && iter.hasNext()) {
				Set<BlockPos> furthestLeaves = iter.next();
				Iterator<BlockPos> setIter = furthestLeaves.iterator();
				while (leavesRemoved < decayPerTick && setIter.hasNext()) {
					BlockPos pos = setIter.next();
					BlockState blockState = level.getBlockState(pos);
					if (blockState.getBlock() == leavesType) {
						Block.dropResources(blockState, level, pos);
						if (level.removeBlock(pos, false)) {
							++leavesRemoved;
						}
					}
					setIter.remove();
				}
				if (furthestLeaves.isEmpty()) {
					iter.remove();
				}
			}
			return leaves.isEmpty();
		}
	}

	private static class CubeBoolArrUtil {
		private final long[] data;
		private final int size;

		private CubeBoolArrUtil(int size) {
			int bits = size * size * size;
			int arrSize = bits / 64 + (bits % 64 > 0 ? 1 : 0);
			data = new long[arrSize];
			this.size = size;
		}

		private boolean get(int i, int j, int k) {
			int index = i * size * size + j * size + k;
			return (data[index / 64] & (1L << (index % 64))) > 0;
		}

		private void set(int i, int j, int k, boolean val) {
			int index = i * size * size + j * size + k;
			if (val) {
				data[index / 64] |= 1L << (index % 64);
			}
			else {
				data[index / 64] &= ~(1L << (index % 64));
			}
		}
	}
}
