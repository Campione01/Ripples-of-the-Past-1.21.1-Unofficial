package com.github.standobyte.jojo.api.timestop;

/**
 * Result of a startup-cost query.
 *
 * <p>{@link Kind#PASS} preserves the core cost, {@link Kind#DENY} rejects the
 * start, and {@link Kind#OVERRIDE} replaces the unmultiplied core cost. The
 * core applies its normal client-broadcast stamina multiplier after resolving
 * this decision.</p>
 */
public final class TimeStopStartupCostDecision {
	private static final TimeStopStartupCostDecision PASS =
			new TimeStopStartupCostDecision(Kind.PASS, 0.0F);
	private static final TimeStopStartupCostDecision DENY =
			new TimeStopStartupCostDecision(Kind.DENY, 0.0F);

	private final Kind kind;
	private final float cost;

	private TimeStopStartupCostDecision(Kind kind, float cost) {
		this.kind = kind;
		this.cost = cost;
	}

	public static TimeStopStartupCostDecision pass() {
		return PASS;
	}

	public static TimeStopStartupCostDecision deny() {
		return DENY;
	}

	public static TimeStopStartupCostDecision override(float cost) {
		if (!Float.isFinite(cost) || cost < 0.0F) {
			throw new IllegalArgumentException(
					"Time-stop startup cost must be finite and non-negative");
		}
		return new TimeStopStartupCostDecision(Kind.OVERRIDE, cost);
	}

	public Kind kind() {
		return kind;
	}

	public boolean isDenied() {
		return kind == Kind.DENY;
	}

	public float resolve(float defaultCost) {
		return kind == Kind.OVERRIDE ? cost : defaultCost;
	}

	public enum Kind {
		PASS,
		DENY,
		OVERRIDE
	}
}
