package com.github.standobyte.jojo.api.block;

/**
 * Resolves addon-owned soft-landing behavior for one entity and contact
 * position. Implementations must not mutate the queried entity because the
 * query runs once for fall damage and once for post-landing movement.
 */
@FunctionalInterface
public interface EntitySoftLandingProvider {
	EntitySoftLandingDecision resolve(EntitySoftLandingQuery query);
}
