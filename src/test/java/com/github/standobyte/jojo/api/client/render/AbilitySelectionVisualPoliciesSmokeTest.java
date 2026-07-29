package com.github.standobyte.jojo.api.client.render;

import java.util.List;
import java.util.OptionalInt;

import com.github.standobyte.jojo.client.AbilitySelectionVisualCorePoliciesSmokeTest;
import com.github.standobyte.jojo.powersystem.ability.Ability;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor.ARGB32;

public final class AbilitySelectionVisualPoliciesSmokeTest {
	private AbilitySelectionVisualPoliciesSmokeTest() {}

	public static void main(String[] args) {
		run();
	}

	public static void run() {
		AbilitySelectionVisualPolicies.resetForTests();
		int defaultTint = 0x40FFFFFF;
		check(AbilitySelectionVisualPolicies.selectionTint(
						null,
						null,
						null,
						null,
						defaultTint) == defaultTint,
				"empty selection policy path changed the default tint");
		check(AbilitySelectionVisualPolicies.selectionTintOverride(
						null,
						null,
						null,
						null,
						defaultTint).isEmpty(),
				"empty selection policy path returned an override");

		ResourceLocation pass = id("pass");
		ResourceLocation failing = id("failing");
		ResourceLocation green = id("green");
		AbilitySelectionVisualPolicies.register(
				pass, query -> OptionalInt.empty());
		AbilitySelectionVisualPolicies.register(
				failing, query -> {
					throw new IllegalStateException(
							"expected selection policy failure");
				});
		AbilitySelectionVisualPolicies.register(
				green, query -> {
					check(query.surface()
									== AbilitySelectionSurface.HOTBAR_SELECTED
							|| query.surface()
									== AbilitySelectionSurface
											.DIRECT_BIND_ACTIVE,
							"selection surface was not preserved");
					check(query.conditionCheck().isPositive(),
							"condition result was not preserved");
					return OptionalInt.of(0xFF00FF00);
				});
		AbilityType<Ability> type = new AbilityType<>(
				id("toggle_type"), Ability::new);
		Ability ability = type.createInstance(new AbilityId(
				null, id("power"), "toggle"));
		int tinted = AbilitySelectionVisualPolicies.selectionTint(
				ability,
				null,
				ConditionCheck.POSITIVE,
				AbilitySelectionSurface.HOTBAR_SELECTED,
				defaultTint);
		check(tinted == ARGB32.multiply(
						defaultTint, 0xFF00FF00),
				"selection tint was not alpha-multiplied");
		OptionalInt directBindTint =
				AbilitySelectionVisualPolicies.selectionTintOverride(
						ability,
						null,
						ConditionCheck.POSITIVE,
						AbilitySelectionSurface.DIRECT_BIND_ACTIVE,
						defaultTint);
		check(directBindTint.isPresent()
						&& directBindTint.getAsInt() == ARGB32.multiply(
								defaultTint, 0xFF00FF00),
				"direct-bind override presence or tint was lost");
		check(AbilitySelectionVisualPolicies.registeredOwners().equals(
						List.of(pass, failing, green)),
				"selection policy order or exception isolation changed");
		expectIllegalState(() ->
				AbilitySelectionVisualPolicies.register(
						pass, query -> OptionalInt.empty()));
		AbilitySelectionVisualPolicies.resetForTests();
		AbilitySelectionVisualCorePoliciesSmokeTest.run();
		AbilitySelectionVisualPolicies.resetForTests();
	}

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath("rotp_test", path);
	}

	private static void expectIllegalState(Runnable action) {
		try {
			action.run();
		}
		catch (IllegalStateException expected) {
			return;
		}
		throw new AssertionError(
				"duplicate selection visual policy was accepted");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
