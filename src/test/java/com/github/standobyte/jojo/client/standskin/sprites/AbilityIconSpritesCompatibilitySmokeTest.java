package com.github.standobyte.jojo.client.standskin.sprites;

import net.minecraft.resources.ResourceLocation;

public final class AbilityIconSpritesCompatibilitySmokeTest {
	private AbilityIconSpritesCompatibilitySmokeTest() {}

	public static void run() {
		ResourceLocation legacy = AbilityIconSprites.memoize2.apply("heavy_punch");
		check("jojo_ripples".equals(legacy.getNamespace()),
				"legacy icon cache must keep the core namespace");
		check("ability/heavy_punch".equals(legacy.getPath()),
				"legacy icon cache must keep accepting String names");

		ResourceLocation addonAbility = ResourceLocation.fromNamespaceAndPath(
				"rotp_test", "heavy_punch");
		ResourceLocation namespaced = AbilityIconSprites.defaultSpritePath(addonAbility);
		check("rotp_test".equals(namespaced.getNamespace()),
				"namespaced icon cache must preserve the addon namespace");
		check("ability/heavy_punch".equals(namespaced.getPath()),
				"namespaced icon cache must use the standard ability path");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
