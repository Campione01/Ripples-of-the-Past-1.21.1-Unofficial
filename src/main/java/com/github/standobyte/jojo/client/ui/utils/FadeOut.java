package com.github.standobyte.jojo.client.ui.utils;

import java.util.ArrayList;
import java.util.Collection;

import org.jetbrains.annotations.ApiStatus;

public class FadeOut {
	public final int ticksMax;
	public final int ticksStartFadeOut;
	public int ticks;
	
	public FadeOut(int ticksMax, int ticksStartFadeOut) {
		this.ticksMax = ticksMax;
		this.ticksStartFadeOut = ticksStartFadeOut;
		this.ticks = 0;
		__TO_TICK.add(this);
	}
	
	public void reset() {
		ticks = ticksMax;
	}
	
	public float getValue(float partialTick) {
		if (ticks >= ticksStartFadeOut) {
			return 1F;
		}
		if (ticks <= 0) {
			return 0F;
		}
		return (ticks - partialTick) / (float) ticksStartFadeOut;
	}
	
	
	@ApiStatus.Internal
	public static final Collection<FadeOut> __TO_TICK = new ArrayList<>();
	
	@ApiStatus.Internal
	public void __tick() {
		if (ticks > 0) {
			ticks--;
		}
	}
}
