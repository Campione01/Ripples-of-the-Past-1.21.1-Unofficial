package com.github.standobyte.jojo.api.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Immutable context supplied by the core when Vanilla asks a block for a
 * signal value.
 */
public record BlockSignalQuery(
		Level level,
		BlockPos position,
		BlockState state,
		Kind kind) {

	public enum Kind {
		PRESSURE_PLATE_OUTPUT
	}
}
