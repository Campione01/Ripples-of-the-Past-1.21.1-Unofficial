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
	public static final String FEATURE_TIME_STOP_LIFECYCLE_V1 =
			"time_stop_lifecycle_v1";
	public static final String FEATURE_ADDON_POST_EFFECT_LIFECYCLE_V1 =
			"addon_post_effect_lifecycle_v1";
	public static final String FEATURE_SCOPED_PLAYER_MODEL_VISIBILITY_V1 =
			"scoped_player_model_visibility_v1";
	public static final String FEATURE_SCOPED_PLAYER_MODEL_POSE_V1 =
			"scoped_player_model_pose_v1";
	public static final String FEATURE_FIRST_PERSON_POST_ARM_LAYERS_V1 =
			"first_person_post_arm_layers_v1";
	public static final String FEATURE_DIRECTIONAL_GRAVITY_V1 =
			"directional_gravity_v1";
	public static final String FEATURE_STAND_MOVESET_EXTENSIONS_V1 =
			"stand_moveset_extensions_v1";
	public static final String FEATURE_PLAYER_POWER_MOVESET_EXTENSIONS_V1 =
			"player_power_moveset_extensions_v1";
	public static final String
			FEATURE_PLAYER_POWER_MOVESET_GROUP_BINDINGS_V1 =
					"player_power_moveset_group_bindings_v1";

	public static final Set<String> FEATURES = Set.of(
			FEATURE_ABILITY_RESOURCE_NAMESPACE_V1,
			FEATURE_STAND_VISUAL_CONTEXT_V1,
			FEATURE_STAND_POWER_TRANSITIONS_V1,
			FEATURE_TIME_STOP_LIFECYCLE_V1,
			FEATURE_ADDON_POST_EFFECT_LIFECYCLE_V1,
			FEATURE_SCOPED_PLAYER_MODEL_VISIBILITY_V1,
			FEATURE_SCOPED_PLAYER_MODEL_POSE_V1,
			FEATURE_FIRST_PERSON_POST_ARM_LAYERS_V1,
			FEATURE_DIRECTIONAL_GRAVITY_V1,
			FEATURE_STAND_MOVESET_EXTENSIONS_V1,
			FEATURE_PLAYER_POWER_MOVESET_EXTENSIONS_V1,
			FEATURE_PLAYER_POWER_MOVESET_GROUP_BINDINGS_V1);

	private RotpAddonApi() {}

	public static boolean supportsAbi(int abiVersion) {
		return abiVersion == ABI_VERSION;
	}

	public static boolean supportsFeature(String feature) {
		return FEATURES.contains(feature);
	}
}
