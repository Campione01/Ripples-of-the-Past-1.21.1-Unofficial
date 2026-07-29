package com.github.standobyte.jojo.api.client.render;

import javax.annotation.Nullable;

@FunctionalInterface
public interface StandMaterialTintPolicy {
	@Nullable
	StandMaterialTint materialTint(StandMaterialTintQuery query);
}
