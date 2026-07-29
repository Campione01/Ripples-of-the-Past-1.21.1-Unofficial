package com.github.standobyte.jojo.subsystems.directional_gravity;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Per-entity state for the nested directional-gravity travel boundary.
 */
public interface DirectionalGravityFrameAccess {
	int jojo_ripples$localFrameDepth();

	void jojo_ripples$setLocalFrameDepth(int depth);

	Direction jojo_ripples$localFrameDirection();

	void jojo_ripples$setLocalFrameDirection(Direction direction);

	Vec3 jojo_ripples$lastLocalMovement();

	void jojo_ripples$setLastLocalMovement(Vec3 movement);

	boolean jojo_ripples$isWorldMoveAdapterActive();

	void jojo_ripples$setWorldMoveAdapterActive(boolean active);
}
