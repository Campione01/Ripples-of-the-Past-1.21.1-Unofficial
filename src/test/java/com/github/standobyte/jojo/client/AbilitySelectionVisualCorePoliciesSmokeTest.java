package com.github.standobyte.jojo.client;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import com.github.standobyte.jojo.api.client.render.AbilitySelectionSurface;
import com.github.standobyte.jojo.api.client.render.AbilitySelectionVisualPolicies;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.powersystem.ability.Ability;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor.ARGB32;

public final class AbilitySelectionVisualCorePoliciesSmokeTest {
	private static final AbilityType<Ability> TEST_TYPE =
			new AbilityType<>(id("test_type"), Ability::new);

	private AbilitySelectionVisualCorePoliciesSmokeTest() {}

	public static void run() {
		AbilitySelectionVisualCorePolicies.register();
		int defaultTint = 0x40FFFFFF;
		int expectedGreen = ARGB32.multiply(
				defaultTint,
				AbilitySelectionVisualCorePolicies.GREEN_TINT);

		List<AbilityId> finishers = List.of(
				standId("star_platinum", "finisher_uppercut"),
				standId("the_world", "kick"),
				standId("silver_chariot", "sweeping_attack"),
				standId("magicians_red", "kick"),
				standId("crazy_diamond", "finisher"),
				standId("gold_experience", "lifeshot_punch"),
				standId("star_platinum", "grab_uppercut"),
				standId("the_world", "grab_kick"),
				standId("crazy_diamond", "grab_finisher"));
		for (AbilityId abilityId : finishers) {
			Ability finisher = ability(abilityId);
			finisher.initIsFinisher();
			check(AbilitySelectionVisualCorePolicies.ruleFor(finisher)
							== AbilitySelectionVisualCorePolicies.Rule
									.CONDITION_POSITIVE,
					"finisher was not classified: " + abilityId);
			check(tint(finisher, ConditionCheck.POSITIVE, defaultTint)
							.orElseThrow() == expectedGreen,
					"ready finisher was not green: " + abilityId);
			check(tint(finisher, ConditionCheck.NEGATIVE, defaultTint)
							.isEmpty(),
					"blocked finisher stayed green: " + abilityId);
		}

		List<AbilityId> followups = List.of(
				standId("crazy_diamond", "disfiguring_punch"),
				standId("crazy_diamond", "leave_object"),
				standId("crazy_diamond", "fuse_with_rock"),
				standId("gold_experience", "lifeshot"),
				standId("gold_experience", "tooth_lifeform"));
		for (AbilityId abilityId : followups) {
			AbilitySelectionVisualCorePolicies.Rule rule =
					AbilitySelectionVisualCorePolicies.ruleFor(
							0,
							abilityId.powerTypeId(),
							abilityId.nameInMoveset());
			check(rule
							== AbilitySelectionVisualCorePolicies.Rule
									.CONDITION_POSITIVE,
					"modifier follow-up was not classified: "
							+ abilityId);
			check(AbilitySelectionVisualCorePolicies.tintForRule(
							rule,
							ConditionCheck.POSITIVE,
							null).orElseThrow()
							== AbilitySelectionVisualCorePolicies.GREEN_TINT,
					"ready modifier follow-up was not green: "
							+ abilityId);
			check(AbilitySelectionVisualCorePolicies.tintForRule(
							rule,
							ConditionCheck.NEGATIVE,
							null)
							.isEmpty(),
					"blocked modifier follow-up stayed green: "
							+ abilityId);
		}

		Map<AbilityId, AbilitySelectionVisualCorePolicies.Rule>
				activeStateAbilities = Map.of(
						playerId("hamon", "rebuff_overdrive"),
						AbilitySelectionVisualCorePolicies.Rule
								.HAMON_REBUFF_ACTIVE,
						playerId("hamon", "hamon_protection"),
						AbilitySelectionVisualCorePolicies.Rule
								.HAMON_PROTECTION_ENABLED,
						playerId("hamon", "wall_climbing"),
						AbilitySelectionVisualCorePolicies.Rule
								.HAMON_WALL_CLIMBING,
						playerId("pillarman", "pillarman_stone_form"),
						AbilitySelectionVisualCorePolicies.Rule
								.PILLARMAN_STONE_FORM_ENABLED,
						playerId("zombie", "zombie_disguise"),
						AbilitySelectionVisualCorePolicies.Rule
								.ZOMBIE_DISGUISE_ENABLED);
		activeStateAbilities.forEach((abilityId, expectedRule) -> {
			AbilitySelectionVisualCorePolicies.Rule rule =
					AbilitySelectionVisualCorePolicies.ruleFor(
							1,
							abilityId.powerTypeId(),
							abilityId.nameInMoveset());
			check(rule
							== expectedRule,
					"active-state ability was not classified: "
							+ abilityId);
			check(AbilitySelectionVisualCorePolicies.tintForRule(
							rule,
							ConditionCheck.POSITIVE,
							null)
							.isEmpty(),
					"active-state ability ignored inactive/null power: "
							+ abilityId);
		});

		check(AbilitySelectionVisualCorePolicies.ruleFor(
						0,
						JojoMod.resLoc("crazy_diamond"),
						"heavy_punch") == null,
				"ordinary heavy punch was classified as green");
		check(AbilitySelectionVisualCorePolicies.ruleFor(
						1,
						JojoMod.resLoc("zombie"),
						"hamon_protection") == null,
				"ability name leaked across power types");
		check(AbilitySelectionVisualCorePolicies.ruleFor(
						1,
						JojoMod.resLoc("crazy_diamond"),
						"disfiguring_punch") == null,
				"stand mapping leaked across power classes");
	}

	private static OptionalInt tint(
			Ability ability,
			ConditionCheck conditionCheck,
			int defaultTint) {
		return AbilitySelectionVisualPolicies.selectionTintOverride(
				ability,
				null,
				conditionCheck,
				AbilitySelectionSurface.DIRECT_BIND_ACTIVE,
				defaultTint);
	}

	private static Ability ability(AbilityId abilityId) {
		return new Ability(TEST_TYPE, abilityId);
	}

	private static AbilityId standId(
			String powerType,
			String abilityName) {
		return new AbilityId(
				null,
				JojoMod.resLoc(powerType),
				abilityName);
	}

	private static AbilityId playerId(
			String powerType,
			String abilityName) {
		return new AbilityId(
				null,
				JojoMod.resLoc(powerType),
				abilityName);
	}

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath("rotp_test", path);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
