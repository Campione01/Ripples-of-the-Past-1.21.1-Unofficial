package com.github.standobyte.jojo.api;

import java.util.Set;

/**
 * Stable feature negotiation surface for independently built ROTP addons.
 */
public final class RotpAddonApi {
	public static final int ABI_VERSION = 1;

	public static final String FEATURE_ABILITY_RESOURCE_NAMESPACE_V1 =
			"ability_resource_namespace_v1";
	public static final String FEATURE_STAND_VISUAL_CONTEXT_V1 =
			"stand_visual_context_v1";
	public static final String FEATURE_STAND_POWER_TRANSITIONS_V1 =
			"stand_power_transitions_v1";

	public static final Set<String> FEATURES = Set.of(
			FEATURE_ABILITY_RESOURCE_NAMESPACE_V1,
			FEATURE_STAND_VISUAL_CONTEXT_V1,
			FEATURE_STAND_POWER_TRANSITIONS_V1);

	private RotpAddonApi() {}

	public static boolean supportsAbi(int abiVersion) {
		return abiVersion == ABI_VERSION;
	}

	public static boolean supportsFeature(String feature) {
		return FEATURES.contains(feature);
	}
}
