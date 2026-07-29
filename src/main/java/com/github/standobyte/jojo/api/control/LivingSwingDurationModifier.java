package com.github.standobyte.jojo.api.control;

@FunctionalInterface
public interface LivingSwingDurationModifier {
	int modifyDuration(LivingSwingDurationQuery query);
}
