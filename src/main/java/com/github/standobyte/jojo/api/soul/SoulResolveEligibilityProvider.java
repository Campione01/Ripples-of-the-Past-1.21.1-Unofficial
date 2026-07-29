package com.github.standobyte.jojo.api.soul;

@FunctionalInterface
public interface SoulResolveEligibilityProvider {
	SoulResolveDecision decide(SoulResolveQuery query);
}
