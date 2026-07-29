package com.github.standobyte.jojo.api.block;

import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Server-authoritative contact query shared by the block fall-damage and
 * post-landing movement hooks.
 */
public record EntitySoftLandingQuery(
		ServerLevel level,
		BlockPos position,
		BlockState blockState,
		Entity entity) {

	public EntitySoftLandingQuery {
		Objects.requireNonNull(level, "level");
		position = Objects.requireNonNull(position, "position").immutable();
		Objects.requireNonNull(blockState, "blockState");
		Objects.requireNonNull(entity, "entity");
	}
}
