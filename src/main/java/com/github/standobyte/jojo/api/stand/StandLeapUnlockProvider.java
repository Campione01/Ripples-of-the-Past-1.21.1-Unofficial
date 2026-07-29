package com.github.standobyte.jojo.api.stand;

/**
 * Supplies an additive, per-user Stand-leap unlock. Implementations must be
 * side-effect free.
 */
@FunctionalInterface
public interface StandLeapUnlockProvider {
	boolean unlocks(StandLeapUnlockQuery query);
}
