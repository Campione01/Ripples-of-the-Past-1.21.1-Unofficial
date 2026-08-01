package com.github.standobyte.jojo.api;

import com.github.standobyte.jojo.api.block.BlockSignalSuppressorsSmokeTest;
import com.github.standobyte.jojo.api.block.BlockRandomTickSuppressionProvidersSmokeTest;
import com.github.standobyte.jojo.api.block.EntitySoftLandingProvidersSmokeTest;
import com.github.standobyte.jojo.api.client.time.ClientRegionalTimeDilationPoliciesSmokeTest;
import com.github.standobyte.jojo.api.client.render.ObserverWorldRenderPoliciesSmokeTest;
import com.github.standobyte.jojo.api.client.render.AbilitySelectionVisualPoliciesSmokeTest;
import com.github.standobyte.jojo.api.client.render.ClientSkyPresentationProvidersSmokeTest;
import com.github.standobyte.jojo.api.client.render.ClientSkyRenderersSmokeTest;
import com.github.standobyte.jojo.api.client.render.EntityMaskPostEffectSmokeTest;
import com.github.standobyte.jojo.api.client.render.EntityPostRenderExtensionsSmokeTest;
import com.github.standobyte.jojo.api.client.render.ItemMaterialTintPoliciesSmokeTest;
import com.github.standobyte.jojo.api.client.render.FirstPersonStandRenderPoliciesSmokeTest;
import com.github.standobyte.jojo.api.client.render.LivingEntityBaseModelTintsSmokeTest;
import com.github.standobyte.jojo.api.client.render.LivingEntityMaterialTintPoliciesSmokeTest;
import com.github.standobyte.jojo.api.client.render.LivingEntityRenderLayerExtensionsSmokeTest;
import com.github.standobyte.jojo.api.client.render.PlayerBaseModelVisibilityPoliciesSmokeTest;
import com.github.standobyte.jojo.api.client.render.PlayerArmPoseProvidersSmokeTest;
import com.github.standobyte.jojo.api.client.render.StandMaterialTintPoliciesSmokeTest;
import com.github.standobyte.jojo.api.client.render.HumanoidModelPostSetupSmokeTest;
import com.github.standobyte.jojo.api.client.render.ScopedHumanoidArmorVisibilitySmokeTest;
import com.github.standobyte.jojo.api.control.CarriedPassengerActionHooksSmokeTest;
import com.github.standobyte.jojo.api.control.CreeperFuseSuppressionProvidersSmokeTest;
import com.github.standobyte.jojo.api.control.LivingSwingDurationModifiersSmokeTest;
import com.github.standobyte.jojo.api.control.ControlledEntityCombatLeasesSmokeTest;
import com.github.standobyte.jojo.api.control.ControlledMobBehaviorLeasesSmokeTest;
import com.github.standobyte.jojo.api.control.PlayerOperationPoliciesSmokeTest;
import com.github.standobyte.jojo.api.healing.CrazyDiamondRestoreExtensionsSmokeTest;
import com.github.standobyte.jojo.api.healing.GoldExperienceExternalHealingTargetsSmokeTest;
import com.github.standobyte.jojo.api.client.vampirism.HungryZombiePoseProvidersSmokeTest;
import com.github.standobyte.jojo.api.item.ItemHandFreePredicatesSmokeTest;
import com.github.standobyte.jojo.api.item.LivingHandUseBlockersSmokeTest;
import com.github.standobyte.jojo.api.leap.LeapAccessPoliciesSmokeTest;
import com.github.standobyte.jojo.api.playerpower.PlayerPowerTypePoliciesSmokeTest;
import com.github.standobyte.jojo.api.playerpower.PlayerPowerDelegationsSmokeTest;
import com.github.standobyte.jojo.api.playerpower.PlayerPowerTransitionsSmokeTest;
import com.github.standobyte.jojo.api.power.PowerSkillUnlocksSmokeTest;
import com.github.standobyte.jojo.api.rps.RpsCheatRegistrationsSmokeTest;
import com.github.standobyte.jojo.api.soul.SoulResolveEligibilityProvidersSmokeTest;
import com.github.standobyte.jojo.api.stand.StandArrowPoolOverridesSmokeTest;
import com.github.standobyte.jojo.api.stand.StandDamageAuthorizersSmokeTest;
import com.github.standobyte.jojo.api.stand.AutomatedStandGrantVetoesSmokeTest;
import com.github.standobyte.jojo.api.stand.StandLeapUnlockProvidersSmokeTest;
import com.github.standobyte.jojo.api.stand.StandPowerTransitionsSmokeTest;
import com.github.standobyte.jojo.api.stand.StandMovesetExtensionsSmokeTest;
import com.github.standobyte.jojo.api.stand.StandVirusMobGiversSmokeTest;
import com.github.standobyte.jojo.api.stonemask.StoneMaskExtensionsSmokeTest;
import com.github.standobyte.jojo.api.timestop.TimeStopAwarenessProvidersSmokeTest;
import com.github.standobyte.jojo.api.timestop.TimeStopEntityMovementAuthorizersSmokeTest;
import com.github.standobyte.jojo.api.trade.ContextualVillagerTradesSmokeTest;
import com.github.standobyte.jojo.api.playerpower.PlayerPowerMovesetExtensionsSmokeTest;
import com.github.standobyte.jojo.api.gravity.DirectionalGravityTransformsSmokeTest;
import com.github.standobyte.jojo.api.gravity.DirectionalGravityDataSmokeTest;
import com.github.standobyte.jojo.api.gravity.DirectionalGravityV2SmokeTest;
import com.github.standobyte.jojo.api.client.render.AddonPostEffectRegistrationSmokeTest;
import com.github.standobyte.jojo.api.client.render.FirstPersonPostArmLayersSmokeTest;
import com.github.standobyte.jojo.api.client.render.ScopedPlayerModelVisibilitySmokeTest;
import com.github.standobyte.jojo.api.client.render.ScopedPlayerModelPoseSmokeTest;
import com.github.standobyte.jojo.api.client.animation.AddonPlayerAnimationsSmokeTest;
import com.github.standobyte.jojo.client.entityrender.parsemodel.generic.GenericModelFormatSmokeTest;
import com.github.standobyte.jojo.client.shader.TimeStopShaderRouteSmokeTest;
import com.github.standobyte.jojo.client.standskin.sprites.AbilityIconSpritesCompatibilitySmokeTest;
import com.github.standobyte.jojo.client.ui.text.StandSkillTextSmokeTest;
import com.github.standobyte.jojo.item.CoreItemResourceSmokeTest;
import com.github.standobyte.jojo.item.StandRemoverItemContractSmokeTest;
import com.github.standobyte.jojo.mechanics.standdisc.StandWrittenOnDiscSmokeTest;
import com.github.standobyte.jojo.mechanics.standarrow.StandVirusMobGiverLifecyclePolicySmokeTest;
import com.github.standobyte.jojo.network.s2c.TrPowerDataPacketSmokeTest;
import com.github.standobyte.jojo.powersystem.ability.Ability;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.condition.GrabContextVariationSmokeTest;
import com.github.standobyte.jojo.powersystem.ability.input.HeldInputControlSmokeTest;
import com.github.standobyte.jojo.powersystem.SharedGrabChargedHeavyInputSmokeTest;
import com.github.standobyte.jojo.powersystem.standpower.StandPowerInstanceChangeSmokeTest;
import com.github.standobyte.jojo.powersystem.standpower.StandRandomWeightSmokeTest;
import com.github.standobyte.jojo.subsystems.rollback.RollbackTransactionFoundationSmokeTest;
import com.github.standobyte.jojo.subsystems.directional_gravity.DirectionalGravityCollisionSmokeTest;
import com.github.standobyte.jojo.subsystems.directional_gravity.DirectionalGravityFrameSmokeTest;
import com.github.standobyte.jojo.subsystems.entity_puppetcontrol.client.ClientEntityControllerCameraSmokeTest;
import com.github.standobyte.jojo.subsystems.entity_puppetcontrol.client.ClientEntityControllerPickSourceSmokeTest;
import com.github.standobyte.jojo.subsystems.timestop.TimeStopRefundPolicySmokeTest;
import com.github.standobyte.jojoimpl.stands.crazydiamond.CrazyDRestoreExtensionSmokeTest;
import com.github.standobyte.jojoimpl.npc.rps.RpsCheatStateSmokeTest;
import com.github.standobyte.jojoimpl.powers.pillarman.abilities.PillarmanAbilityDataOwnershipSmokeTest;

import net.minecraft.resources.ResourceLocation;

public final class RotpAddonApiSmokeTest {
	private RotpAddonApiSmokeTest() {}

	public static void main(String[] args) {
		check(RotpAddonApi.supportsAbi(1), "ABI 1 must be supported");
		check(!RotpAddonApi.supportsAbi(2), "unknown ABI must not be supported");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi.FEATURE_ABILITY_RESOURCE_NAMESPACE_V1),
				"ability namespace feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi.FEATURE_STAND_POWER_TRANSITIONS_V1),
				"Stand power transitions feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi.FEATURE_STAND_POWER_TRANSITIONS_V2),
				"Stand power transitions v2 feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi.FEATURE_STAND_POWER_TRANSITIONS_V3),
				"Stand power transitions v3 feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi.FEATURE_STAND_RANDOM_WEIGHTS_V1),
				"Stand random weights feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi.FEATURE_STAND_ARROW_POOL_OVERRIDES_V1),
				"Stand Arrow pool overrides feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi.FEATURE_TIME_STOP_LIFECYCLE_V1),
				"time-stop lifecycle feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi
						.FEATURE_TIME_STOP_AWARENESS_PROVIDERS_V1),
				"time-stop awareness providers feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi.FEATURE_ADDON_POST_EFFECT_LIFECYCLE_V1),
				"addon post-effect lifecycle feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi
						.FEATURE_SCOPED_PLAYER_MODEL_VISIBILITY_V1),
				"scoped player-model visibility feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi
						.FEATURE_PLAYER_BASE_MODEL_VISIBILITY_POLICIES_V1),
				"player base-model visibility policies feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi.FEATURE_SCOPED_PLAYER_MODEL_POSE_V1),
				"scoped player-model pose feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi
						.FEATURE_SCOPED_HUMANOID_ARMOR_VISIBILITY_V1),
				"scoped humanoid-armor visibility feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi.FEATURE_HUMANOID_MODEL_POST_SETUP_V1),
				"humanoid-model post-setup feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi.FEATURE_FIRST_PERSON_POST_ARM_LAYERS_V1),
				"first-person post-arm layers feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi
						.FEATURE_LIVING_ENTITY_RENDER_LAYER_EXTENSIONS_V1),
				"living entity render-layer extensions feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi.FEATURE_ADDON_PLAYER_ANIMATIONS_V1),
				"addon player animations feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi.FEATURE_DIRECTIONAL_GRAVITY_V1),
				"directional gravity feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi.FEATURE_DIRECTIONAL_GRAVITY_V2),
				"directional gravity v2 feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi.FEATURE_STAND_MOVESET_EXTENSIONS_V1),
				"Stand moveset extensions feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi.FEATURE_STAND_MOVESET_REPLACEMENTS_V1),
				"Stand moveset replacements feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi
						.FEATURE_PLAYER_POWER_MOVESET_EXTENSIONS_V1),
				"PlayerPower moveset extensions feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi
						.FEATURE_PLAYER_POWER_MOVESET_REPLACEMENTS_V1),
				"PlayerPower moveset replacements feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi
						.FEATURE_PLAYER_POWER_MOVESET_GROUP_BINDINGS_V1),
				"PlayerPower moveset group bindings feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi.FEATURE_BLOCK_SIGNAL_SUPPRESSORS_V1),
				"block signal suppressors feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi
						.FEATURE_ENTITY_SOFT_LANDING_PROVIDERS_V1),
				"entity soft-landing providers feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi.FEATURE_TYPED_POWER_DATA_SYNC_V1),
				"typed power-data sync feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi.FEATURE_STAND_DAMAGE_AUTHORIZERS_V1),
				"Stand damage authorizers feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi.FEATURE_STAND_LEAP_UNLOCK_PROVIDERS_V1),
				"Stand leap unlock providers feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi.FEATURE_STAND_VIRUS_MOB_GIVERS_V1),
				"Stand-virus mob givers feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi.FEATURE_AUTOMATED_STAND_GRANT_VETOES_V1),
				"Automated Stand grant vetoes feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi.FEATURE_STONE_MASK_EXTENSIONS_V1),
				"Stone Mask extensions feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi
						.FEATURE_PLAYER_POWER_TEMPORARY_TRANSITIONS_V1),
				"PlayerPower temporary transitions feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi
						.FEATURE_PLAYER_POWER_TEMPORARY_TRANSITIONS_V2),
				"PlayerPower temporary transitions v2 feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi
						.FEATURE_PLAYER_POWER_DELEGATION_PROVIDERS_V1),
				"PlayerPower delegation providers feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi
						.FEATURE_CONTEXTUAL_VILLAGER_TRADES_V1),
				"contextual villager trades feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi
						.FEATURE_HUNGRY_ZOMBIE_POSE_PROVIDERS_V1),
				"Hungry Zombie pose providers feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi.FEATURE_ITEM_USE_PARTICLE_PROVIDER_V1),
				"item-use particle provider feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi.FEATURE_ITEM_HAND_FREE_PREDICATES_V1),
				"item hand-free predicates feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi.FEATURE_SERVER_HELD_INPUT_CONTROL_V1),
				"server held-input control feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi.FEATURE_PLAYER_POWER_TYPE_POLICIES_V1),
				"PlayerPower type policies feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi.FEATURE_EXPLICIT_POWER_SKILL_UNLOCKS_V1),
				"explicit power-skill unlocks feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi.FEATURE_TIME_STOP_REFUND_POLICY_V1),
				"time-stop refund policy feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi
						.FEATURE_TIME_STOP_ENTITY_MOVEMENT_AUTHORIZERS_V1),
				"time-stop entity movement authorizers feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi.FEATURE_OBSERVER_WORLD_RENDER_POLICIES_V1),
				"observer world render policies feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi.FEATURE_CONTROLLED_ENTITY_COMBAT_LEASES_V1),
				"controlled-entity combat leases feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi.FEATURE_CONTROLLED_MOB_BEHAVIOR_LEASES_V1),
				"controlled-mob behavior leases feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi
						.FEATURE_CLIENT_ENTITY_CONTROLLER_PICK_SOURCE_V1),
				"controller pick-source feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi
						.FEATURE_CLIENT_ENTITY_CONTROLLER_LOCAL_CAMERA_V1),
				"controller local-camera feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi
						.FEATURE_CARRIED_PASSENGER_ACTION_HOOKS_V1),
				"carried-passenger action hooks feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi
						.FEATURE_SOUL_RESOLVE_ELIGIBILITY_PROVIDERS_V1),
				"soul Resolve eligibility providers feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi
						.FEATURE_ABILITY_SELECTION_VISUAL_POLICIES_V1),
				"ability selection visual policies feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi
						.FEATURE_CREEPER_FUSE_SUPPRESSION_PROVIDERS_V1),
				"Creeper fuse suppression providers feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi
						.FEATURE_LIVING_BASE_MODEL_TINT_PROVIDERS_V1),
				"living base-model tint providers feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi
						.FEATURE_ITEM_MATERIAL_TINT_POLICIES_V1),
				"item material tint policies feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi
						.FEATURE_ENTITY_POST_RENDER_EXTENSIONS_V1),
				"entity post-render extensions feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi
						.FEATURE_CLIENT_REGIONAL_TIME_DILATION_POLICIES_V1),
				"client regional time-dilation policies feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi.FEATURE_ENTITY_MASK_POST_EFFECT_V1),
				"entity mask post-effect feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi
						.FEATURE_BLOCK_RANDOM_TICK_SUPPRESSION_PROVIDERS_V1),
				"block random-tick suppression providers feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi
						.FEATURE_LIVING_SWING_DURATION_MODIFIERS_V1),
				"living swing-duration modifiers feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi.FEATURE_PLAYER_ARM_POSE_PROVIDERS_V1),
				"player arm-pose providers feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi
						.FEATURE_FIRST_PERSON_STAND_RENDER_POLICIES_V1),
				"first-person Stand render policies feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi.FEATURE_STAND_MATERIAL_TINT_POLICIES_V1),
				"Stand material tint policies feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi
						.FEATURE_LIVING_ENTITY_MATERIAL_TINT_POLICIES_V1),
				"living entity material tint policies feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi
						.FEATURE_CLIENT_SKY_PRESENTATION_PROVIDERS_V1),
				"client sky presentation providers feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi.FEATURE_CLIENT_SKY_RENDERERS_V1),
				"client sky renderers feature missing");

		AbilityType<Ability> addonType = new AbilityType<>(
				id("rotp_test", "freeze"), Ability::new);
		Ability addonAbility = addonType.createInstance(new AbilityId(
				null, id("rotp_echoes", "echoes_act3"), "freeze"));

		check("rotp_echoes".equals(addonAbility.getResourceNamespace()),
				"ability namespace must follow its power type");
		check("rotp_echoes.ability.freeze".equals(addonAbility.getTranslationKey()),
				"ability translation key must follow its power type namespace");
		check(id("rotp_echoes", "freeze").equals(addonAbility.getSpriteId(null)),
				"ability sprite ID must follow its power type namespace");

		Ability registryNamespaceFallback = addonType.createInstance(
				new AbilityId(null, null, "freeze"));
		check("rotp_test".equals(registryNamespaceFallback.getResourceNamespace()),
				"ability type namespace must be the fallback without a power type");
		check("rotp_test.ability.freeze".equals(
				registryNamespaceFallback.getTranslationKey()),
				"fallback translation key must use the ability type namespace");

		AbilityType<Ability> coreType = new AbilityType<>(
				id("jojo_ripples", "light_attack"), Ability::new);
		Ability coreAbility = coreType.createInstance(new AbilityId(
				null, id("jojo_ripples", "star_platinum"), "light_attack"));
		check("jojo_ripples.ability.light_attack".equals(
				coreAbility.getTranslationKey()),
				"built-in translation key must remain unchanged");
		check(id("jojo_ripples", "light_attack").equals(
				coreAbility.getSpriteId(null)),
				"built-in sprite ID must remain unchanged");

		StandPowerTransitionsSmokeTest.run();
		SoulResolveEligibilityProvidersSmokeTest.run();
		AbilitySelectionVisualPoliciesSmokeTest.run();
		LivingEntityBaseModelTintsSmokeTest.run();
		ItemMaterialTintPoliciesSmokeTest.run();
		BlockRandomTickSuppressionProvidersSmokeTest.run();
		LivingSwingDurationModifiersSmokeTest.run();
		PlayerArmPoseProvidersSmokeTest.run();
		FirstPersonStandRenderPoliciesSmokeTest.run();
		StandMaterialTintPoliciesSmokeTest.run();
		LivingEntityMaterialTintPoliciesSmokeTest.run();
		ClientSkyPresentationProvidersSmokeTest.run();
		ClientSkyRenderersSmokeTest.run();
		EntityPostRenderExtensionsSmokeTest.run();
		EntityMaskPostEffectSmokeTest.run();
		LivingEntityRenderLayerExtensionsSmokeTest.run();
		StandMovesetExtensionsSmokeTest.run();
		PlayerPowerMovesetExtensionsSmokeTest.run();
		AddonPostEffectRegistrationSmokeTest.run();
		FirstPersonPostArmLayersSmokeTest.run();
		AddonPlayerAnimationsSmokeTest.run();
		ScopedPlayerModelVisibilitySmokeTest.run();
		PlayerBaseModelVisibilityPoliciesSmokeTest.run();
		ScopedPlayerModelPoseSmokeTest.run();
		ScopedHumanoidArmorVisibilitySmokeTest.run();
		HumanoidModelPostSetupSmokeTest.run();
		StandWrittenOnDiscSmokeTest.run();
		CoreItemResourceSmokeTest.run();
		StandRemoverItemContractSmokeTest.run();
		StandPowerInstanceChangeSmokeTest.run();
		StandRandomWeightSmokeTest.run();
		StandArrowPoolOverridesSmokeTest.run();
		GrabContextVariationSmokeTest.run();
		SharedGrabChargedHeavyInputSmokeTest.run();
		CrazyDRestoreExtensionSmokeTest.run();
		GenericModelFormatSmokeTest.run();
		AbilityIconSpritesCompatibilitySmokeTest.run();
		StandSkillTextSmokeTest.run();
		RollbackTransactionFoundationSmokeTest.run();
		DirectionalGravityTransformsSmokeTest.run();
		DirectionalGravityDataSmokeTest.run();
		DirectionalGravityV2SmokeTest.run();
		DirectionalGravityCollisionSmokeTest.run();
		DirectionalGravityFrameSmokeTest.run();
		TimeStopShaderRouteSmokeTest.run();
		TimeStopAwarenessProvidersSmokeTest.run();
		BlockSignalSuppressorsSmokeTest.run();
		EntitySoftLandingProvidersSmokeTest.run();
		TrPowerDataPacketSmokeTest.run();
		StandDamageAuthorizersSmokeTest.run();
		StandLeapUnlockProvidersSmokeTest.run();
		StandVirusMobGiversSmokeTest.run();
		StandVirusMobGiverLifecyclePolicySmokeTest.run();
		AutomatedStandGrantVetoesSmokeTest.run();
		StoneMaskExtensionsSmokeTest.run();
		PlayerPowerTransitionsSmokeTest.run();
		PlayerPowerDelegationsSmokeTest.run();
		ContextualVillagerTradesSmokeTest.run();
		HungryZombiePoseProvidersSmokeTest.run();
		CoreExtensionRegressionSmokeTest.run();
		ItemHandFreePredicatesSmokeTest.run();
		HeldInputControlSmokeTest.run();
		PlayerPowerTypePoliciesSmokeTest.run();
		PowerSkillUnlocksSmokeTest.run();
		TimeStopRefundPolicySmokeTest.run();
		TimeStopEntityMovementAuthorizersSmokeTest.run();
		ClientRegionalTimeDilationPoliciesSmokeTest.run();
		ObserverWorldRenderPoliciesSmokeTest.run();
		ControlledEntityCombatLeasesSmokeTest.run();
		ControlledMobBehaviorLeasesSmokeTest.run();
		ClientEntityControllerPickSourceSmokeTest.run();
		ClientEntityControllerCameraSmokeTest.run();
		CarriedPassengerActionHooksSmokeTest.run();
		CreeperFuseSuppressionProvidersSmokeTest.run();
		PlayerOperationPoliciesSmokeTest.run();
		LeapAccessPoliciesSmokeTest.run();
		RpsCheatRegistrationsSmokeTest.run();
		RpsCheatStateSmokeTest.run();
		HeavensDoorCoreBoundarySmokeTest.run();
		PillarmanAbilityDataOwnershipSmokeTest.run();
		CrazyDiamondRestoreExtensionsSmokeTest.run();
		GoldExperienceExternalHealingTargetsSmokeTest.run();
		LivingHandUseBlockersSmokeTest.run();
	}

	private static ResourceLocation id(String namespace, String path) {
		return ResourceLocation.fromNamespaceAndPath(namespace, path);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
