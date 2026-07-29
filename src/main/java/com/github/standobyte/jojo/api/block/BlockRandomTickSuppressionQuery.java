package com.github.standobyte.jojo.api.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Server-owned context for one scheduled block random tick.
 */
public record BlockRandomTickSuppressionQuery(
		ServerLevel level,
		BlockPos position,
		BlockState state) {}
