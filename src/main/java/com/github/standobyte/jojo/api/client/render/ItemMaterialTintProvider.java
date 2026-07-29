package com.github.standobyte.jojo.api.client.render;

import javax.annotation.Nullable;

/**
 * Claims a standard baked-item render by returning a material tint.
 */
@FunctionalInterface
public interface ItemMaterialTintProvider {
	@Nullable ItemMaterialTint materialTint(ItemMaterialTintQuery query);
}
