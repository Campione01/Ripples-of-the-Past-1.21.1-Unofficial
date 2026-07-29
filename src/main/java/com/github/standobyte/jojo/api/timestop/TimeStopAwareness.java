package com.github.standobyte.jojo.api.timestop;

/**
 * Addon-provided access to an active time stop. Movement access always
 * implies visual awareness.
 */
public record TimeStopAwareness(boolean canSee, boolean canMove) {
	public static final TimeStopAwareness NONE =
			new TimeStopAwareness(false, false);
	public static final TimeStopAwareness SEE_ONLY =
			new TimeStopAwareness(true, false);
	public static final TimeStopAwareness FULL =
			new TimeStopAwareness(true, true);

	public TimeStopAwareness {
		if (canMove) {
			canSee = true;
		}
	}

	public TimeStopAwareness merge(TimeStopAwareness other) {
		if (other == null) {
			return this;
		}
		return new TimeStopAwareness(
				canSee || other.canSee,
				canMove || other.canMove);
	}
}
