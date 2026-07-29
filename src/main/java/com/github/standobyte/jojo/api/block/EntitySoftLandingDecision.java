package com.github.standobyte.jojo.api.block;

/**
 * Result of an all-entity soft-landing query.
 *
 * <p>{@link Kind#PASS} preserves vanilla block behavior.
 * {@link Kind#SOFT_BOUNCE} replaces fall damage with the supplied multiplier
 * and reflects downward vertical movement with the supplied bounce
 * multiplier.</p>
 */
public final class EntitySoftLandingDecision {
	private static final EntitySoftLandingDecision PASS =
			new EntitySoftLandingDecision(Kind.PASS, 1.0F, 0.0D);

	private final Kind kind;
	private final float fallDamageMultiplier;
	private final double verticalBounceMultiplier;

	private EntitySoftLandingDecision(
			Kind kind,
			float fallDamageMultiplier,
			double verticalBounceMultiplier) {
		this.kind = kind;
		this.fallDamageMultiplier = fallDamageMultiplier;
		this.verticalBounceMultiplier = verticalBounceMultiplier;
	}

	public static EntitySoftLandingDecision pass() {
		return PASS;
	}

	public static EntitySoftLandingDecision softBounce(
			float fallDamageMultiplier,
			double verticalBounceMultiplier) {
		if (!Float.isFinite(fallDamageMultiplier)
				|| fallDamageMultiplier < 0.0F) {
			throw new IllegalArgumentException(
					"fallDamageMultiplier must be finite and non-negative");
		}
		if (!Double.isFinite(verticalBounceMultiplier)
				|| verticalBounceMultiplier < 0.0D) {
			throw new IllegalArgumentException(
					"verticalBounceMultiplier must be finite and non-negative");
		}
		return new EntitySoftLandingDecision(
				Kind.SOFT_BOUNCE,
				fallDamageMultiplier,
				verticalBounceMultiplier);
	}

	public Kind kind() {
		return kind;
	}

	public boolean isHandled() {
		return kind == Kind.SOFT_BOUNCE;
	}

	public float fallDamageMultiplier() {
		return fallDamageMultiplier;
	}

	public double verticalBounceMultiplier() {
		return verticalBounceMultiplier;
	}

	public enum Kind {
		PASS,
		SOFT_BOUNCE
	}
}
