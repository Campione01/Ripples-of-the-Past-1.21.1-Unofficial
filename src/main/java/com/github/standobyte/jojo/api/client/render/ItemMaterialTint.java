package com.github.standobyte.jojo.api.client.render;

/**
 * Transforms a final per-vertex ARGB color for a baked item model.
 */
@FunctionalInterface
public interface ItemMaterialTint {
	int transformArgb(int originalArgb);
}
