package com.github.standobyte.jojo.api.control;

@FunctionalInterface
public interface PlayerOperationPolicy {
	PlayerOperationDecision decide(PlayerOperationQuery query);
}
