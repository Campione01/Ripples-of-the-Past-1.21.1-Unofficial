package com.github.standobyte.jojo.api.client.render;

import javax.annotation.Nullable;

@FunctionalInterface
public interface LivingEntityMaterialTintPolicy {
	@Nullable
	LivingEntityMaterialTint materialTint(
			LivingEntityMaterialTintQuery query);
}
