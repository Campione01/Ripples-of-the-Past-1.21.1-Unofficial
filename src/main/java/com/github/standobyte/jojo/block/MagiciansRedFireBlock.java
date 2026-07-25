package com.github.standobyte.jojo.block;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.github.standobyte.jojo.init.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;

public class MagiciansRedFireBlock extends FireBlock {

	public MagiciansRedFireBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockState getStateForPlacement(BlockGetter level, BlockPos pos) {
		return super.getStateForPlacement(level, pos);
	}

	// Preserve the original MR fire state when MR fire spreads through vanilla FireBlock ticking.
	public BlockState getStateWithAge(LevelAccessor level, BlockPos pos, int age) {
		return getStateForPlacement(level, pos).setValue(AGE, Integer.valueOf(age));
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		List<BlockPos> candidates = fireSpreadCandidates(pos);
		Set<BlockPos> preExistingVanillaFire = new HashSet<>();
		for (BlockPos candidate : candidates) {
			if (level.getBlockState(candidate).is(Blocks.FIRE)) {
				preExistingVanillaFire.add(candidate);
			}
		}

		super.tick(state, level, pos, random);

		for (BlockPos candidate : candidates) {
			if (preExistingVanillaFire.contains(candidate)) {
				continue;
			}
			BlockState spreadState = level.getBlockState(candidate);
			if (spreadState.is(Blocks.FIRE)) {
				level.setBlock(candidate, ModBlocks.MAGICIANS_RED_FIRE.get().getStateWithAge(
						level, candidate, spreadState.getValue(AGE)), 3);
			}
		}
	}

	private static List<BlockPos> fireSpreadCandidates(BlockPos origin) {
		List<BlockPos> candidates = new ArrayList<>(74);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 4; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx != 0 || dy != 0 || dz != 0) {
						candidates.add(origin.offset(dx, dy, dz));
					}
				}
			}
		}
		return candidates;
	}
}
