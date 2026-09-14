package rotp.core.api.client.render;

import java.util.OptionalInt;

@FunctionalInterface
public interface AbilitySelectionVisualPolicy {
	OptionalInt selectionTint(AbilitySelectionVisualQuery query);
}
