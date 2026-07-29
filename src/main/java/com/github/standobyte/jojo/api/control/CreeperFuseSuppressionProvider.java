package com.github.standobyte.jojo.api.control;

import net.minecraft.world.entity.monster.Creeper;

/**
 * Server-safe predicate for suppressing a Creeper's fuse and explosion.
 */
@FunctionalInterface
public interface CreeperFuseSuppressionProvider {
	boolean shouldSuppress(Creeper creeper);
}
