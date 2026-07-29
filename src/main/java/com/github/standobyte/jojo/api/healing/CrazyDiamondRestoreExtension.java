package com.github.standobyte.jojo.api.healing;

import net.minecraft.world.entity.Entity;

/**
 * Addon-owned Crazy Diamond restoration behavior.
 *
 * <p>Target matching must be side-effect free. Mutation callbacks are invoked
 * by the server restoration action only.</p>
 */
public interface CrazyDiamondRestoreExtension {
	default boolean canTarget(Entity target) {
		return false;
	}

	default ExternalRestoreResult restoreExternal(
			ExternalRestoreContext context) {
		return ExternalRestoreResult.unhandled();
	}

	default RestoreAugmentation afterLivingRestoreAttempt(
			LivingRestoreContext context) {
		return RestoreAugmentation.none();
	}
}
