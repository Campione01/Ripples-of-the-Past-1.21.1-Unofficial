package com.github.standobyte.jojo.api.leap;

@FunctionalInterface
public interface LeapAccessPolicy {
	boolean denies(LeapAccessQuery query);
}
