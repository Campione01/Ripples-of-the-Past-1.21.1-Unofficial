package com.github.standobyte.jojo.client.input.clickhold;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.client.input.controlscheme.ClientKey;

import net.minecraft.client.Minecraft;

public class AmbiguousKeyPress {
	public static final float timeIsHold = 4; // 200 ms
	public static final float timeAssumeHold = 2; // 100 ms
	public static final float guardClickTimeIsHold = 8; // 400 ms
	public static final float guardClickTimeAssumeHold = 4; // 200 ms
	private final float timeIsHoldThreshold;
	private final float timeAssumeHoldThreshold;
	private InputState curState = null;
	private float timeHeld;
	
	public InputResolved onHold;
	public InputResolved onClick;
	public DualClickHandler onDualKeyClick;

	public AmbiguousKeyPress() {
		this(timeAssumeHold, timeIsHold);
	}

	public AmbiguousKeyPress(float timeAssumeHoldThreshold, float timeIsHoldThreshold) {
		this.timeAssumeHoldThreshold = timeAssumeHoldThreshold;
		this.timeIsHoldThreshold = timeIsHoldThreshold;
	}
	
	@Nullable
	public Result frameUpdate(float tickDelta) {
		timeHeld += tickDelta;
		InputState newState = timeHeld < timeAssumeHoldThreshold ? null : timeHeld < timeIsHoldThreshold ? InputState.ASSUME_HOLD : InputState.HOLD;
		if (newState != curState) {
			this.curState = newState;
			float ticks = toGameTicks(timeHeld);
			return new Result(newState, ticks);
		}
		return null;
	}
	
	public Result keyReleased() {
		float ticks = toGameTicks(timeHeld);
		return new Result(timeHeld < timeIsHoldThreshold ? InputState.CLICK : InputState.HOLD, ticks);
	}
	
	public static float toGameTicks(float frameTicks) {
		return frameTicks / 20 * Minecraft.getInstance().level.tickRateManager().tickrate();
	}
	
	
	public enum InputState {
		ASSUME_HOLD,
		HOLD,
		CLICK
	}
	
	public static record Result(InputState input, float timeTook) {}
	
	@FunctionalInterface public static interface InputResolved {
		void handleInput(float timeTook);
	}
	@FunctionalInterface public static interface DualClickHandler {
		boolean checkHandleInput(ClientKey secondKeyPressed, float timeTook);
	}
}
