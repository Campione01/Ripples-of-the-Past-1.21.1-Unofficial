package com.github.standobyte.jojo.client.standskin.sprites;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojoimpl.stands._entitybase.StandEntityGrabReleaseAbility;

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

		AbilityType<StandEntityGrabReleaseAbility> releaseType = new AbilityType<>(
				ResourceLocation.fromNamespaceAndPath("jojo_ripples", "stand_grab_release"),
				StandEntityGrabReleaseAbility::new);
		StandEntityGrabReleaseAbility release = releaseType.createInstance(new AbilityId(
				null, ResourceLocation.fromNamespaceAndPath("rotp_test", "test_stand"), "grab_release"));
		check(ResourceLocation.fromNamespaceAndPath("jojo_ripples", "stand_grab_release")
				.equals(release.getSpriteId(null)), "addon release must use the shared cancellation icon");
		check("jojo_ripples.ability.grab_release".equals(release.getTranslationKey()),
				"automatically added release must use its existing core translation");
		try (InputStream releaseIcon = AbilityIconSpritesCompatibilitySmokeTest.class.getResourceAsStream(
				"/assets/jojo_ripples/textures/ability/stand_grab_release.png");
				InputStream originalCancel = AbilityIconSpritesCompatibilitySmokeTest.class.getResourceAsStream(
						"/assets/jojo_ripples/textures/ability/hamon_rebuff_overdrive_cancel.png")) {
			check(releaseIcon != null && originalCancel != null, "shared release icon must be packaged");
			check(Arrays.equals(releaseIcon.readAllBytes(), originalCancel.readAllBytes()),
					"release must reuse the original cancellation symbol, not a punch or shield");
		}
		catch (IOException exception) {
			throw new AssertionError("Could not read the shared release icon", exception);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
