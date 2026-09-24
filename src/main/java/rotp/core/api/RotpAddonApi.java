package rotp.core.api;

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
	public static final String FEATURE_STAND_POWER_TRANSITIONS_V2 =
			"stand_power_transitions_v2";
	public static final String FEATURE_STAND_POWER_TRANSITIONS_V3 =
			"stand_power_transitions_v3";
	public static final String FEATURE_STAND_RANDOM_WEIGHTS_V1 =
			"stand_random_weights_v1";
	public static final String FEATURE_STAND_ARROW_POOL_OVERRIDES_V1 =
			"stand_arrow_pool_overrides_v1";
	public static final String FEATURE_TIME_STOP_LIFECYCLE_V1 =
			"time_stop_lifecycle_v1";
	public static final String FEATURE_TIME_STOP_AWARENESS_PROVIDERS_V1 =
			"time_stop_awareness_providers_v1";
	public static final String FEATURE_ADDON_POST_EFFECT_LIFECYCLE_V1 =
			"addon_post_effect_lifecycle_v1";
	public static final String FEATURE_SCOPED_PLAYER_MODEL_VISIBILITY_V1 =
			"scoped_player_model_visibility_v1";
	public static final String
			FEATURE_PLAYER_BASE_MODEL_VISIBILITY_POLICIES_V1 =
					"player_base_model_visibility_policies_v1";
	public static final String FEATURE_SCOPED_PLAYER_MODEL_POSE_V1 =
			"scoped_player_model_pose_v1";
	public static final String FEATURE_SCOPED_HUMANOID_ARMOR_VISIBILITY_V1 =
			"scoped_humanoid_armor_visibility_v1";
	public static final String FEATURE_HUMANOID_MODEL_POST_SETUP_V1 =
			"humanoid_model_post_setup_v1";
	public static final String FEATURE_FIRST_PERSON_POST_ARM_LAYERS_V1 =
			"first_person_post_arm_layers_v1";
	public static final String FEATURE_ADDON_PLAYER_ANIMATIONS_V1 =
			"addon_player_animations_v1";
	public static final String FEATURE_DIRECTIONAL_GRAVITY_V1 =
			"directional_gravity_v1";
	public static final String FEATURE_DIRECTIONAL_GRAVITY_V2 =
			"directional_gravity_v2";
	public static final String FEATURE_STAND_MOVESET_EXTENSIONS_V1 =
			"stand_moveset_extensions_v1";
	public static final String FEATURE_STAND_MOVESET_REPLACEMENTS_V1 =
			"stand_moveset_replacements_v1";
	public static final String FEATURE_PLAYER_POWER_MOVESET_EXTENSIONS_V1 =
			"player_power_moveset_extensions_v1";
	public static final String FEATURE_PLAYER_POWER_MOVESET_REPLACEMENTS_V1 =
			"player_power_moveset_replacements_v1";
	public static final String
			FEATURE_PLAYER_POWER_MOVESET_GROUP_BINDINGS_V1 =
					"player_power_moveset_group_bindings_v1";
	public static final String FEATURE_BLOCK_SIGNAL_SUPPRESSORS_V1 =
			"block_signal_suppressors_v1";
	public static final String FEATURE_ENTITY_SOFT_LANDING_PROVIDERS_V1 =
			"entity_soft_landing_providers_v1";
	public static final String FEATURE_TYPED_POWER_DATA_SYNC_V1 =
			"typed_power_data_sync_v1";
	public static final String FEATURE_STAND_DAMAGE_AUTHORIZERS_V1 =
			"stand_damage_authorizers_v1";
	public static final String FEATURE_STAND_LEAP_UNLOCK_PROVIDERS_V1 =
			"stand_leap_unlock_providers_v1";
	public static final String FEATURE_STAND_VIRUS_MOB_GIVERS_V1 =
			"stand_virus_mob_givers_v1";
	public static final String FEATURE_AUTOMATED_STAND_GRANT_VETOES_V1 =
			"automated_stand_grant_vetoes_v1";
	public static final String FEATURE_STONE_MASK_EXTENSIONS_V1 =
			"stone_mask_extensions_v1";
	public static final String
			FEATURE_PLAYER_POWER_TEMPORARY_TRANSITIONS_V1 =
					"player_power_temporary_transitions_v1";
	public static final String
			FEATURE_PLAYER_POWER_TEMPORARY_TRANSITIONS_V2 =
					"player_power_temporary_transitions_v2";
	public static final String
			FEATURE_PLAYER_POWER_DELEGATION_PROVIDERS_V1 =
					"player_power_delegation_providers_v1";
	public static final String
			FEATURE_CONTEXTUAL_VILLAGER_TRADES_V1 =
					"contextual_villager_trades_v1";
	public static final String FEATURE_HUNGRY_ZOMBIE_POSE_PROVIDERS_V1 =
			"hungry_zombie_pose_providers_v1";
	public static final String FEATURE_ITEM_USE_PARTICLE_PROVIDER_V1 =
			"item_use_particle_provider_v1";
	public static final String FEATURE_ITEM_HAND_FREE_PREDICATES_V1 =
			"item_hand_free_predicates_v1";
	public static final String FEATURE_SERVER_HELD_INPUT_CONTROL_V1 =
			"server_held_input_control_v1";
	public static final String FEATURE_PLAYER_POWER_TYPE_POLICIES_V1 =
			"player_power_type_policies_v1";
	public static final String FEATURE_EXPLICIT_POWER_SKILL_UNLOCKS_V1 =
			"explicit_power_skill_unlocks_v1";
	public static final String FEATURE_TIME_STOP_REFUND_POLICY_V1 =
			"time_stop_refund_policy_v1";
	public static final String
			FEATURE_TIME_STOP_ENTITY_MOVEMENT_AUTHORIZERS_V1 =
					"time_stop_entity_movement_authorizers_v1";
	public static final String FEATURE_OBSERVER_WORLD_RENDER_POLICIES_V1 =
			"observer_world_render_policies_v1";
	public static final String FEATURE_CONTROLLED_ENTITY_COMBAT_LEASES_V1 =
			"controlled_entity_combat_leases_v1";
	public static final String FEATURE_CONTROLLED_MOB_BEHAVIOR_LEASES_V1 =
			"controlled_mob_behavior_leases_v1";
	public static final String
			FEATURE_CLIENT_ENTITY_CONTROLLER_PICK_SOURCE_V1 =
					"client_entity_controller_pick_source_v1";
	public static final String
			FEATURE_CLIENT_ENTITY_CONTROLLER_LOCAL_CAMERA_V1 =
					"client_entity_controller_local_camera_v1";
	public static final String FEATURE_CARRIED_PASSENGER_ACTION_HOOKS_V1 =
			"carried_passenger_action_hooks_v1";
	public static final String FEATURE_SOUL_RESOLVE_ELIGIBILITY_PROVIDERS_V1 =
			"soul_resolve_eligibility_providers_v1";
	public static final String FEATURE_ABILITY_SELECTION_VISUAL_POLICIES_V1 =
			"ability_selection_visual_policies_v1";
	public static final String FEATURE_CREEPER_FUSE_SUPPRESSION_PROVIDERS_V1 =
			"creeper_fuse_suppression_providers_v1";
	public static final String FEATURE_LIVING_BASE_MODEL_TINT_PROVIDERS_V1 =
			"living_base_model_tint_providers_v1";
	public static final String FEATURE_ITEM_MATERIAL_TINT_POLICIES_V1 =
			"item_material_tint_policies_v1";
	public static final String FEATURE_LIVING_ENTITY_RENDER_LAYER_EXTENSIONS_V1 =
			"living_entity_render_layer_extensions_v1";
	public static final String FEATURE_ENTITY_POST_RENDER_EXTENSIONS_V1 =
			"entity_post_render_extensions_v1";
	public static final String
			FEATURE_CLIENT_REGIONAL_TIME_DILATION_POLICIES_V1 =
					"client_regional_time_dilation_policies_v1";
	public static final String FEATURE_ENTITY_MASK_POST_EFFECT_V1 =
			"entity_mask_post_effect_v1";
	public static final String
			FEATURE_BLOCK_RANDOM_TICK_SUPPRESSION_PROVIDERS_V1 =
					"block_random_tick_suppression_providers_v1";
	public static final String FEATURE_LIVING_SWING_DURATION_MODIFIERS_V1 =
			"living_swing_duration_modifiers_v1";
	public static final String FEATURE_PLAYER_ARM_POSE_PROVIDERS_V1 =
			"player_arm_pose_providers_v1";
	public static final String
			FEATURE_FIRST_PERSON_STAND_RENDER_POLICIES_V1 =
					"first_person_stand_render_policies_v1";
	public static final String FEATURE_STAND_MATERIAL_TINT_POLICIES_V1 =
			"stand_material_tint_policies_v1";
	public static final String FEATURE_LIVING_ENTITY_MATERIAL_TINT_POLICIES_V1 =
			"living_entity_material_tint_policies_v1";
	public static final String
			FEATURE_CLIENT_SKY_PRESENTATION_PROVIDERS_V1 =
					"client_sky_presentation_providers_v1";
	public static final String FEATURE_CLIENT_SKY_RENDERERS_V1 =
			"client_sky_renderers_v1";
	/**
	 * Time stops and blinks tuned to their own 1.16 values: TimeStopBlinkAbility base costs and
	 * blink sound, TimeStopAbility#shortensChargeAtResolveFour, TimeStopLearning#onBlinkPunchTimeSkip.
	 */
	public static final String FEATURE_TIME_STOP_ABILITY_TUNING_V1 =
			"time_stop_ability_tuning_v1";
	/** ResolveCounter#addAttackerResolveMultTier (1.16 StandType.Builder#addAttackerResolveMultTier). */
	public static final String FEATURE_STAND_ATTACKER_RESOLVE_TIERS_V1 =
			"stand_attacker_resolve_tiers_v1";
	/**
	 * EntityActionAbility held-action rechecks and hit hooks: checkHeldActionConditions (each held tick and, through
	 * canFireReleasedHold, before a released hold fires: power usable, performer alive and not stunned, the Stand
	 * parts the action needs, then the ability's own checkHeldSpecificConditions; not the cooldown or a busy
	 * performer), canFireReleasedHold, stopsOnHeavyAttack / onHitByHeavyAttack, setResetsAttackStrengthOnPerform.
	 */
	public static final String FEATURE_ENTITY_ACTION_HOOKS_V1 =
			"entity_action_hooks_v1";
	/**
	 * A guarding Stand also guards the hits aimed at its user (1.16 standBlockUserAttack): StandEntity#guardsHitsOnUser
	 * (a Stand that cuts its user's hits itself returns false) and #getDamageBlockMultiplier, and
	 * NoKnockbackOnBlocking#setOneTickKbRes / #hasOneTickKbRes / #cancelHurtSound, and StandUserGuard#blockedWhole
	 * (a hit the guard took whole; 1.16 cancelled it, so damage handlers such as counters skip it).
	 */
	public static final String FEATURE_STAND_USER_GUARD_V1 =
			"stand_user_guard_v1";
	/**
	 * TheWorldTSPunchAbility#setStaminaCost / #setTrainsTimeStop, and a TS punch prices its time skip from the
	 * moveset's time_stop_blink (1.16 TheWorldTSHeavyAttack was built with its blink), so an add-on blink's own
	 * base costs reach its TS punch.
	 */
	public static final String FEATURE_TIME_STOP_PUNCH_TUNING_V1 =
			"time_stop_punch_tuning_v1";
	/** TimeStopBlinkAbility#setEntityTargetTeleportPos (1.16 TimeStopInstant#getEntityTargetTeleportPos). */
	public static final String FEATURE_TIME_STOP_BLINK_TARGET_POS_V1 =
			"time_stop_blink_target_pos_v1";
	/** TimeStopAbility#setHeldWalkSpeed / #getHeldWalkSpeed (1.16 TimeStop.Builder#heldWalkSpeed; the core's stays 1). */
	public static final String FEATURE_TIME_STOP_HELD_WALK_SPEED_V1 =
			"time_stop_held_walk_speed_v1";
	/** Ability#getRequiredResolveLevel(Power): the Resolve gate with per-Stand overrides, which add-ons could not read. */
	public static final String FEATURE_ABILITY_REQUIRED_RESOLVE_LEVEL_V1 =
			"ability_required_resolve_level_v1";
	/**
	 * 1.16 Action#playVoiceLine's sneak rule: Ability#sayShout / #sayShoutOf / #skipsShoutWhileSneaking, and
	 * #setPlaysVoiceLineOnSneak / #playsVoiceLineOnSneak for a former SHIFT variation or a heavy finisher.
	 */
	public static final String FEATURE_ABILITY_SHOUTS_V1 =
			"ability_shouts_v1";

	public static final Set<String> FEATURES = Set.of(
			FEATURE_ABILITY_RESOURCE_NAMESPACE_V1,
			FEATURE_STAND_VISUAL_CONTEXT_V1,
			FEATURE_STAND_POWER_TRANSITIONS_V1,
			FEATURE_STAND_POWER_TRANSITIONS_V2,
			FEATURE_STAND_POWER_TRANSITIONS_V3,
			FEATURE_STAND_RANDOM_WEIGHTS_V1,
			FEATURE_STAND_ARROW_POOL_OVERRIDES_V1,
			FEATURE_TIME_STOP_LIFECYCLE_V1,
			FEATURE_TIME_STOP_AWARENESS_PROVIDERS_V1,
			FEATURE_ADDON_POST_EFFECT_LIFECYCLE_V1,
			FEATURE_SCOPED_PLAYER_MODEL_VISIBILITY_V1,
			FEATURE_PLAYER_BASE_MODEL_VISIBILITY_POLICIES_V1,
			FEATURE_SCOPED_PLAYER_MODEL_POSE_V1,
			FEATURE_SCOPED_HUMANOID_ARMOR_VISIBILITY_V1,
			FEATURE_HUMANOID_MODEL_POST_SETUP_V1,
			FEATURE_FIRST_PERSON_POST_ARM_LAYERS_V1,
			FEATURE_ADDON_PLAYER_ANIMATIONS_V1,
			FEATURE_DIRECTIONAL_GRAVITY_V1,
			FEATURE_DIRECTIONAL_GRAVITY_V2,
			FEATURE_STAND_MOVESET_EXTENSIONS_V1,
			FEATURE_STAND_MOVESET_REPLACEMENTS_V1,
			FEATURE_PLAYER_POWER_MOVESET_EXTENSIONS_V1,
			FEATURE_PLAYER_POWER_MOVESET_REPLACEMENTS_V1,
			FEATURE_PLAYER_POWER_MOVESET_GROUP_BINDINGS_V1,
			FEATURE_BLOCK_SIGNAL_SUPPRESSORS_V1,
			FEATURE_ENTITY_SOFT_LANDING_PROVIDERS_V1,
			FEATURE_TYPED_POWER_DATA_SYNC_V1,
			FEATURE_STAND_DAMAGE_AUTHORIZERS_V1,
			FEATURE_STAND_LEAP_UNLOCK_PROVIDERS_V1,
			FEATURE_STAND_VIRUS_MOB_GIVERS_V1,
			FEATURE_AUTOMATED_STAND_GRANT_VETOES_V1,
			FEATURE_STONE_MASK_EXTENSIONS_V1,
			FEATURE_PLAYER_POWER_TEMPORARY_TRANSITIONS_V1,
			FEATURE_PLAYER_POWER_TEMPORARY_TRANSITIONS_V2,
			FEATURE_PLAYER_POWER_DELEGATION_PROVIDERS_V1,
			FEATURE_CONTEXTUAL_VILLAGER_TRADES_V1,
			FEATURE_HUNGRY_ZOMBIE_POSE_PROVIDERS_V1,
			FEATURE_ITEM_USE_PARTICLE_PROVIDER_V1,
			FEATURE_ITEM_HAND_FREE_PREDICATES_V1,
			FEATURE_SERVER_HELD_INPUT_CONTROL_V1,
			FEATURE_PLAYER_POWER_TYPE_POLICIES_V1,
			FEATURE_EXPLICIT_POWER_SKILL_UNLOCKS_V1,
			FEATURE_TIME_STOP_REFUND_POLICY_V1,
			FEATURE_TIME_STOP_ENTITY_MOVEMENT_AUTHORIZERS_V1,
			FEATURE_OBSERVER_WORLD_RENDER_POLICIES_V1,
			FEATURE_CONTROLLED_ENTITY_COMBAT_LEASES_V1,
			FEATURE_CONTROLLED_MOB_BEHAVIOR_LEASES_V1,
			FEATURE_CLIENT_ENTITY_CONTROLLER_PICK_SOURCE_V1,
			FEATURE_CLIENT_ENTITY_CONTROLLER_LOCAL_CAMERA_V1,
			FEATURE_CARRIED_PASSENGER_ACTION_HOOKS_V1,
			FEATURE_SOUL_RESOLVE_ELIGIBILITY_PROVIDERS_V1,
			FEATURE_ABILITY_SELECTION_VISUAL_POLICIES_V1,
			FEATURE_CREEPER_FUSE_SUPPRESSION_PROVIDERS_V1,
			FEATURE_LIVING_BASE_MODEL_TINT_PROVIDERS_V1,
			FEATURE_ITEM_MATERIAL_TINT_POLICIES_V1,
			FEATURE_LIVING_ENTITY_RENDER_LAYER_EXTENSIONS_V1,
			FEATURE_ENTITY_POST_RENDER_EXTENSIONS_V1,
			FEATURE_CLIENT_REGIONAL_TIME_DILATION_POLICIES_V1,
			FEATURE_ENTITY_MASK_POST_EFFECT_V1,
			FEATURE_BLOCK_RANDOM_TICK_SUPPRESSION_PROVIDERS_V1,
			FEATURE_LIVING_SWING_DURATION_MODIFIERS_V1,
			FEATURE_PLAYER_ARM_POSE_PROVIDERS_V1,
			FEATURE_FIRST_PERSON_STAND_RENDER_POLICIES_V1,
			FEATURE_STAND_MATERIAL_TINT_POLICIES_V1,
			FEATURE_LIVING_ENTITY_MATERIAL_TINT_POLICIES_V1,
			FEATURE_CLIENT_SKY_PRESENTATION_PROVIDERS_V1,
			FEATURE_CLIENT_SKY_RENDERERS_V1,
			FEATURE_TIME_STOP_ABILITY_TUNING_V1,
			FEATURE_STAND_ATTACKER_RESOLVE_TIERS_V1,
			FEATURE_ENTITY_ACTION_HOOKS_V1,
			FEATURE_STAND_USER_GUARD_V1,
			FEATURE_TIME_STOP_PUNCH_TUNING_V1,
			FEATURE_TIME_STOP_BLINK_TARGET_POS_V1,
			FEATURE_TIME_STOP_HELD_WALK_SPEED_V1,
			FEATURE_ABILITY_REQUIRED_RESOLVE_LEVEL_V1,
			FEATURE_ABILITY_SHOUTS_V1);

	private RotpAddonApi() {}

	public static boolean supportsAbi(int abiVersion) {
		return abiVersion == ABI_VERSION;
	}

	public static boolean supportsFeature(String feature) {
		return FEATURES.contains(feature);
	}
}
