package rotp.core.api.client.render;

import java.util.Set;

import rotp.core.api.client.render.ScopedPlayerModelVisibility.Part;

/**
 * Selects vanilla player parts to hide for one base-model draw.
 */
@FunctionalInterface
public interface PlayerBaseModelVisibilityPolicy {
	Set<Part> hiddenParts(PlayerBaseModelVisibilityQuery query);
}
