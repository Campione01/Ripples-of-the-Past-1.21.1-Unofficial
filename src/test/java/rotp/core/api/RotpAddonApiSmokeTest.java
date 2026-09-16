package rotp.core.api;

import rotp.core.api.block.BlockSignalSuppressorsSmokeTest;
import rotp.core.api.block.BlockRandomTickSuppressionProvidersSmokeTest;
import rotp.core.api.block.EntitySoftLandingProvidersSmokeTest;
import rotp.core.api.client.time.ClientRegionalTimeDilationPoliciesSmokeTest;
import rotp.core.api.client.render.ObserverWorldRenderPoliciesSmokeTest;
import rotp.core.api.client.render.AbilitySelectionVisualPoliciesSmokeTest;
import rotp.core.api.client.render.ClientSkyPresentationProvidersSmokeTest;
import rotp.core.api.client.render.ClientSkyRenderersSmokeTest;
import rotp.core.api.client.render.EntityMaskPostEffectSmokeTest;
import rotp.core.api.client.render.EntityPostRenderExtensionsSmokeTest;
import rotp.core.api.client.render.ItemMaterialTintPoliciesSmokeTest;
import rotp.core.api.client.render.FirstPersonStandRenderPoliciesSmokeTest;
import rotp.core.api.client.render.LivingEntityBaseModelTintsSmokeTest;
import rotp.core.api.client.render.LivingEntityMaterialTintPoliciesSmokeTest;
import rotp.core.api.client.render.LivingEntityRenderLayerExtensionsSmokeTest;
import rotp.core.api.client.render.PlayerBaseModelVisibilityPoliciesSmokeTest;
import rotp.core.api.client.render.PlayerArmPoseProvidersSmokeTest;
import rotp.core.api.client.render.StandMaterialTintPoliciesSmokeTest;
import rotp.core.api.client.render.HumanoidModelPostSetupSmokeTest;
import rotp.core.api.client.render.ScopedHumanoidArmorVisibilitySmokeTest;
import rotp.core.api.control.CarriedPassengerActionHooksSmokeTest;
import rotp.core.api.control.CreeperFuseSuppressionProvidersSmokeTest;
import rotp.core.api.control.LivingSwingDurationModifiersSmokeTest;
import rotp.core.api.control.ControlledEntityCombatLeasesSmokeTest;
import rotp.core.api.control.ControlledMobBehaviorLeasesSmokeTest;
import rotp.core.api.control.PlayerOperationPoliciesSmokeTest;
import rotp.core.api.healing.CrazyDiamondRestoreExtensionsSmokeTest;
import rotp.core.api.healing.GoldExperienceExternalHealingTargetsSmokeTest;
import rotp.core.api.client.vampirism.HungryZombiePoseProvidersSmokeTest;
import rotp.core.api.item.ItemHandFreePredicatesSmokeTest;
import rotp.core.api.item.LivingHandUseBlockersSmokeTest;
import rotp.core.api.leap.LeapAccessPoliciesSmokeTest;
import rotp.core.api.playerpower.PlayerPowerTypePoliciesSmokeTest;
import rotp.core.api.playerpower.PlayerPowerDelegationsSmokeTest;
import rotp.core.api.playerpower.PlayerPowerTransitionsSmokeTest;
import rotp.core.api.power.PowerSkillUnlocksSmokeTest;
import rotp.core.api.rps.RpsCheatRegistrationsSmokeTest;
import rotp.core.api.soul.SoulResolveEligibilityProvidersSmokeTest;
import rotp.core.api.stand.StandArrowPoolOverridesSmokeTest;
import rotp.core.api.stand.StandDamageAuthorizersSmokeTest;
import rotp.core.api.stand.AutomatedStandGrantVetoesSmokeTest;
import rotp.core.api.stand.StandLeapUnlockProvidersSmokeTest;
import rotp.core.api.stand.StandPowerTransitionsSmokeTest;
import rotp.core.api.stand.StandMovesetExtensionsSmokeTest;
import rotp.core.api.stand.StandVirusMobGiversSmokeTest;
import rotp.core.api.stonemask.StoneMaskExtensionsSmokeTest;
import rotp.core.api.timestop.TimeStopAwarenessProvidersSmokeTest;
import rotp.core.api.timestop.TimeStopEntityMovementAuthorizersSmokeTest;
import rotp.core.api.trade.ContextualVillagerTradesSmokeTest;
import rotp.core.api.playerpower.PlayerPowerMovesetExtensionsSmokeTest;
import rotp.core.api.gravity.DirectionalGravityTransformsSmokeTest;
import rotp.core.api.gravity.DirectionalGravityDataSmokeTest;
import rotp.core.api.gravity.DirectionalGravityV2SmokeTest;
import rotp.core.api.client.render.AddonPostEffectRegistrationSmokeTest;
import rotp.core.api.client.render.FirstPersonPostArmLayersSmokeTest;
import rotp.core.api.client.render.ScopedPlayerModelVisibilitySmokeTest;
import rotp.core.api.client.render.ScopedPlayerModelPoseSmokeTest;
import rotp.core.api.client.animation.AddonPlayerAnimationsSmokeTest;
import rotp.core.client.entityanim.AnimPoseThreadIsolationSmokeTest;
import rotp.core.client.entityrender.parsemodel.generic.GenericModelFormatSmokeTest;
import rotp.core.client.shader.core.EntityOutlinePostChainCompatSmokeTest;
import rotp.core.client.shader.TimeStopShaderRouteSmokeTest;
import rotp.core.client.standskin.sprites.AbilityIconSpritesCompatibilitySmokeTest;
import rotp.core.client.ui.text.StandSkillTextSmokeTest;
import rotp.core.command.commands.JojoConfigCommandSmokeTest;
import rotp.core.item.CoreItemResourceSmokeTest;
import rotp.core.item.StandRemoverItemContractSmokeTest;
import rotp.core.mechanics.standdisc.StandWrittenOnDiscSmokeTest;
import rotp.core.mechanics.standarrow.StandVirusMobGiverLifecyclePolicySmokeTest;
import rotp.core.network.s2c.TrPowerDataPacketSmokeTest;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.GrabContextVariationSmokeTest;
import rotp.core.powersystem.ability.input.HeldInputControlSmokeTest;
import rotp.core.powersystem.SharedGrabChargedHeavyInputSmokeTest;
import rotp.core.powersystem.standpower.StandPowerInstanceChangeSmokeTest;
import rotp.core.powersystem.standpower.StandRandomWeightSmokeTest;
import rotp.core.subsystems.rollback.RollbackTransactionFoundationSmokeTest;
import rotp.core.subsystems.directional_gravity.DirectionalGravityCollisionSmokeTest;
import rotp.core.subsystems.directional_gravity.DirectionalGravityFrameSmokeTest;
import rotp.core.subsystems.entity_puppetcontrol.client.ClientEntityControllerCameraSmokeTest;
import rotp.core.subsystems.entity_puppetcontrol.client.ClientEntityControllerPickSourceSmokeTest;
import rotp.core.subsystems.timestop.TimeStopLearningContractSmokeTest;
import rotp.core.subsystems.timestop.TimeStopRefundPolicySmokeTest;
import rotp.core.impl.stands.crazydiamond.CrazyDRestoreExtensionSmokeTest;
import rotp.core.impl.stands.starplatinum.StarFingerVisualOwnershipSmokeTest;
import rotp.core.impl.npc.rps.RpsCheatStateSmokeTest;
import rotp.core.impl.powers.pillarman.abilities.PillarmanAbilityDataOwnershipSmokeTest;

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
		JojoConfigCommandSmokeTest.run();
		StandArrowPoolOverridesSmokeTest.run();
		GrabContextVariationSmokeTest.run();
		SharedGrabChargedHeavyInputSmokeTest.run();
		CrazyDRestoreExtensionSmokeTest.run();
		GenericModelFormatSmokeTest.run();
		AnimPoseThreadIsolationSmokeTest.run();
		AbilityIconSpritesCompatibilitySmokeTest.run();
		StandSkillTextSmokeTest.run();
		RollbackTransactionFoundationSmokeTest.run();
		DirectionalGravityTransformsSmokeTest.run();
		DirectionalGravityDataSmokeTest.run();
		DirectionalGravityV2SmokeTest.run();
		DirectionalGravityCollisionSmokeTest.run();
		DirectionalGravityFrameSmokeTest.run();
		EntityOutlinePostChainCompatSmokeTest.run();
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
		TimeStopLearningContractSmokeTest.run();
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
		StarFingerVisualOwnershipSmokeTest.run();
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
