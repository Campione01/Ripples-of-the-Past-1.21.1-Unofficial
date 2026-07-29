package com.github.standobyte.jojo.api.client.time;

import org.jetbrains.annotations.ApiStatus;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Mutable fractional-tick state for one regional client surface.
 */
@ApiStatus.Internal
@OnlyIn(Dist.CLIENT)
public final class ClientRegionalTimeDilationTickAccumulator {
	private double accumulatedTicks;

	/**
	 * Adds one real tick's budget and reports whether a logical tick is due.
	 */
	public boolean advance(float factor) {
		if (!Float.isFinite(factor)
				|| factor <= 0.0F
				|| factor > 1.0F) {
			throw new IllegalArgumentException(
					"Regional time-dilation factor must be in (0, 1]");
		}
		if (factor == 1.0F) {
			accumulatedTicks = 0.0D;
			return true;
		}

		double next = accumulatedTicks + (double) factor;
		if (next < 1.0D) {
			accumulatedTicks = next;
			return false;
		}
		accumulatedTicks = next - 1.0D;
		return true;
	}
}
