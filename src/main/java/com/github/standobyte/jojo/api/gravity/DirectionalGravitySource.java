package com.github.standobyte.jojo.api.gravity;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;

/**
 * Supplies authoritative directional-gravity state for one explicitly bound
 * entity. Implementations own persistence and synchronization of that state.
 */
@FunctionalInterface
public interface DirectionalGravitySource {
	Direction gravityDirection(Entity entity);
}
