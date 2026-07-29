package com.github.standobyte.jojo.api.stand;

@FunctionalInterface
public interface StandDamageAuthorizer {
	boolean canHurtStand(StandDamageQuery query);
}
