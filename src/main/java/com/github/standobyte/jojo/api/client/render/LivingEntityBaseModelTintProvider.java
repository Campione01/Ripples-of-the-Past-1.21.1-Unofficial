package com.github.standobyte.jojo.api.client.render;

import java.util.OptionalInt;

/**
 * Resolves an ARGB tint for one living entity's base-model render call.
 */
@FunctionalInterface
public interface LivingEntityBaseModelTintProvider {
	OptionalInt baseModelTint(LivingEntityBaseModelTintQuery query);
}
