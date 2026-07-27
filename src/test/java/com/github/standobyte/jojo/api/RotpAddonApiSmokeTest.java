package com.github.standobyte.jojo.api;

import com.github.standobyte.jojo.api.stand.StandPowerTransitionsSmokeTest;
import com.github.standobyte.jojo.api.stand.StandMovesetExtensionsSmokeTest;
import com.github.standobyte.jojo.api.gravity.DirectionalGravityTransformsSmokeTest;
import com.github.standobyte.jojo.api.gravity.DirectionalGravityDataSmokeTest;
import com.github.standobyte.jojo.api.client.render.AddonPostEffectRegistrationSmokeTest;
import com.github.standobyte.jojo.client.standskin.sprites.AbilityIconSpritesCompatibilitySmokeTest;
import com.github.standobyte.jojo.client.ui.text.StandSkillTextSmokeTest;
import com.github.standobyte.jojo.mechanics.standdisc.StandWrittenOnDiscSmokeTest;
import com.github.standobyte.jojo.powersystem.ability.Ability;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.condition.GrabContextVariationSmokeTest;
import com.github.standobyte.jojo.powersystem.SharedGrabChargedHeavyInputSmokeTest;
import com.github.standobyte.jojo.powersystem.standpower.StandPowerInstanceChangeSmokeTest;
import com.github.standobyte.jojo.subsystems.rollback.RollbackTransactionFoundationSmokeTest;
import com.github.standobyte.jojoimpl.stands.crazydiamond.CrazyDRestoreExtensionSmokeTest;

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
				RotpAddonApi.FEATURE_TIME_STOP_LIFECYCLE_V1),
				"time-stop lifecycle feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi.FEATURE_ADDON_POST_EFFECT_LIFECYCLE_V1),
				"addon post-effect lifecycle feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi.FEATURE_DIRECTIONAL_GRAVITY_V1),
				"directional gravity feature missing");
		check(RotpAddonApi.supportsFeature(
				RotpAddonApi.FEATURE_STAND_MOVESET_EXTENSIONS_V1),
				"Stand moveset extensions feature missing");

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
		StandMovesetExtensionsSmokeTest.run();
		AddonPostEffectRegistrationSmokeTest.run();
		StandWrittenOnDiscSmokeTest.run();
		StandPowerInstanceChangeSmokeTest.run();
		GrabContextVariationSmokeTest.run();
		SharedGrabChargedHeavyInputSmokeTest.run();
		CrazyDRestoreExtensionSmokeTest.run();
		AbilityIconSpritesCompatibilitySmokeTest.run();
		StandSkillTextSmokeTest.run();
		RollbackTransactionFoundationSmokeTest.run();
		DirectionalGravityTransformsSmokeTest.run();
		DirectionalGravityDataSmokeTest.run();
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
