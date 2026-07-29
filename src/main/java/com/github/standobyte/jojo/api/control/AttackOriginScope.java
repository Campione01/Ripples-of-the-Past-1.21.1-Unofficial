package com.github.standobyte.jojo.api.control;

/**
 * Attack origins covered by a controlled-entity combat lease.
 */
public enum AttackOriginScope {
	SELF_ONLY(false),
	SELF_AND_SUMMONED_STAND(true);

	private final boolean includesSummonedStand;

	AttackOriginScope(boolean includesSummonedStand) {
		this.includesSummonedStand = includesSummonedStand;
	}

	public boolean includesSummonedStand() {
		return includesSummonedStand;
	}
}
