package com.github.standobyte.jojo.api.client.render;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Client-only extension invoked after the registered renderer finishes
 * rendering one exact entity.
 */
@OnlyIn(Dist.CLIENT)
public interface EntityPostRenderExtension {
	void afterEntityRender(EntityPostRenderContext context);

	/**
	 * Called once after the entity render stage. Implementations can use this
	 * boundary to discard requests that were not consumed during the frame.
	 */
	default void endFrame(long frameId) {}
}
