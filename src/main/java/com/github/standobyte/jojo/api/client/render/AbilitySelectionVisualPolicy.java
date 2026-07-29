package com.github.standobyte.jojo.api.client.render;

import java.util.OptionalInt;

@FunctionalInterface
public interface AbilitySelectionVisualPolicy {
	OptionalInt selectionTint(AbilitySelectionVisualQuery query);
}
