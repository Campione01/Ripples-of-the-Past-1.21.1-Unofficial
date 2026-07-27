package com.github.standobyte.jojo.powersystem.ability.condition;

import com.github.standobyte.jojo.powersystem.ability.Ability;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;

import net.minecraft.resources.ResourceLocation;

public final class GrabContextVariationSmokeTest {
	private GrabContextVariationSmokeTest() {}

	public static void run() {
		AbilityType<Ability> type = new AbilityType<>(
				id("jojo_ripples", "grab_context_test"), Ability::new);
		Ability base = type.createInstance(new AbilityId(
				null, id("jojo_ripples", "test_stand"), "punch"));
		Ability grabVariation = type.createInstance(new AbilityId(
				null, id("jojo_ripples", "test_stand"), "grab_punch"));

		AvailableAbilities locked = new AvailableAbilities();
		locked._inMoveset.put("punch", locked.getContainerFor(base));
		check(locked.getContextVariationOrDisable(
				"punch", "grab_punch") == null,
				"a locked grab variation must not resolve");
		check(!locked.getConditionCheck("punch").isPositive(),
				"a missing grab variation must disable the base input");

		AvailableAbilities unlocked = new AvailableAbilities();
		unlocked._inMoveset.put("punch", unlocked.getContainerFor(base));
		unlocked._inMoveset.put(
				"grab_punch", unlocked.getContainerFor(grabVariation));
		check(unlocked.getContextVariationOrDisable(
				"punch", "grab_punch") == grabVariation,
				"an unlocked grab variation must replace the base input");
		check(unlocked.getConditionCheck("punch").isPositive(),
				"resolving a grab variation must not disable the base slot");
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
