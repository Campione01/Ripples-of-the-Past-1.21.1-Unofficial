package com.github.standobyte.jojo.api.client.render;

import java.util.Set;

import com.github.standobyte.jojo.api.client.render.ScopedPlayerModelVisibility.Part;

/**
 * Selects vanilla player parts to hide for one base-model draw.
 */
@FunctionalInterface
public interface PlayerBaseModelVisibilityPolicy {
	Set<Part> hiddenParts(PlayerBaseModelVisibilityQuery query);
}
