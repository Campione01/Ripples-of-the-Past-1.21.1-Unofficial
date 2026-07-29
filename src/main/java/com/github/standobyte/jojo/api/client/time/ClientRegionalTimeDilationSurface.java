package com.github.standobyte.jojo.api.client.time;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Narrow client surfaces that can consume a regional time-dilation factor.
 */
@OnlyIn(Dist.CLIENT)
public enum ClientRegionalTimeDilationSurface {
	TIMER,
	PARTICLE,
	SOUND
}
