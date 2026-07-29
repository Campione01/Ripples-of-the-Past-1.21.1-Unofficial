package com.github.standobyte.jojo.api.client.time;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Resolves a factor in the range {@code (0, 1]}. A value of {@code 1}
 * preserves vanilla timing.
 */
@FunctionalInterface
@OnlyIn(Dist.CLIENT)
public interface ClientRegionalTimeDilationProvider {
	float factor(ClientRegionalTimeDilationQuery query);
}
