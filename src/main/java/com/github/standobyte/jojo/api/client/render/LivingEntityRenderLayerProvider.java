package com.github.standobyte.jojo.api.client.render;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Renders one addon-owned pass for the exact living entity and model that are
 * currently being rendered.
 */
@FunctionalInterface
@OnlyIn(Dist.CLIENT)
public interface LivingEntityRenderLayerProvider {
	void render(LivingEntityRenderLayerContext context);
}
