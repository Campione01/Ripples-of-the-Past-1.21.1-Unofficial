package com.github.standobyte.jojo.client.entityanim.action;

import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;

public class AnimActionPhase {
	public final ActionPhase phase;
	public final Mode timeAnimMode;
	public final float loopBackTo;
	
	public AnimActionPhase(ActionPhase phase, Mode timeAnimMode) {
		this(phase, timeAnimMode, 0);
	}
	
	private AnimActionPhase(ActionPhase phase, Mode timeAnimMode, float loopBackTo) {
		this.phase = phase;
		this.timeAnimMode = timeAnimMode;
		this.loopBackTo = loopBackTo;
	}
	
	public static AnimActionPhase loopBack(ActionPhase phase, float loopBackTo) {
		return new AnimActionPhase(phase, Mode.LOOP_BACK, loopBackTo);
	}
	
	
	
	public enum Mode {
		FIT_PHASE_LENGTH,
		CONSTANT_LENGTH,
		LOOP_BACK;
	}
}
