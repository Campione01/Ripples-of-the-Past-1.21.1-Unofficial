package com.github.standobyte.jojo.api.playerpower;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.github.standobyte.jojo.powersystem.Moveset;
import com.github.standobyte.jojo.powersystem.MovesetBuilder;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.ability.Ability;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.controls.ControlSchemeTemplate;
import com.github.standobyte.jojo.powersystem.ability.controls.InputKey;
import com.github.standobyte.jojo.powersystem.ability.controls.InputMethod;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPowerData;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPowerType;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;

public final class PlayerPowerMovesetExtensionsSmokeTest {
	private static final ResourceLocation ORDERED_TARGET =
			id("rotp_test", "ordered_player_power");
	private static final AbilityType<Ability> ANCHOR_TYPE =
			abilityType("anchor");
	private static final AbilityType<Ability> TAIL_TYPE =
			abilityType("tail");
	private static final AbilityType<Ability> LOW_A_TYPE =
			abilityType("low_a");
	private static final AbilityType<Ability> LOW_B_TYPE =
			abilityType("low_b");
	private static final AbilityType<Ability> ALPHA_TYPE =
			abilityType("alpha");
	private static final AbilityType<Ability> ZETA_TYPE =
			abilityType("zeta");

	private PlayerPowerMovesetExtensionsSmokeTest() {}

	public static void run() {
		verifyRestrictedBuilderSurface();
		registerOrderedExtensions();
		verifyDuplicateConflictAndRevision();
		verifyRepeatedApplicationAndOrdering();
		verifyPlayerPowerTypeLifecycle();
		verifyHotbarSlotVariation();
		verifyLateRegistrationRefreshesCaches();
		verifyNoCrossPowerLeakage();
		verifyMissingReferencesAndConflictsFailFast();
	}

	private static void registerOrderedExtensions() {
		PlayerPowerMovesetExtensions.register(
				orderedExtension(
						ORDERED_TARGET,
						id("rotp_test", "zeta_extension"),
						20,
						List.of("zeta"),
						List.of(ZETA_TYPE)));
		PlayerPowerMovesetExtensions.register(
				orderedExtension(
						ORDERED_TARGET,
						id("rotp_test", "low_extension"),
						10,
						List.of("low_a", "low_b"),
						List.of(LOW_A_TYPE, LOW_B_TYPE)));
		PlayerPowerMovesetExtensions.register(
				orderedExtension(
						ORDERED_TARGET,
						id("rotp_test", "alpha_extension"),
						20,
						List.of("alpha"),
						List.of(ALPHA_TYPE)));
	}

	private static void verifyDuplicateConflictAndRevision() {
		long before =
				PlayerPowerMovesetExtensions.targetRevision(
						ORDERED_TARGET);
		PlayerPowerMovesetExtensions.register(
				orderedExtension(
						ORDERED_TARGET,
						id("rotp_test", "alpha_extension"),
						20,
						List.of("alpha"),
						List.of(ALPHA_TYPE)));
		check(PlayerPowerMovesetExtensions.targetRevision(
						ORDERED_TARGET) == before,
				"equal registration must not increment revision");

		expectFailure(
				() -> PlayerPowerMovesetExtensions.register(
						orderedExtension(
								ORDERED_TARGET,
								id("rotp_test", "alpha_extension"),
								21,
								List.of("alpha"),
								List.of(ALPHA_TYPE))),
				"Conflicting PlayerPower moveset extension definition");
	}

	private static void verifyRepeatedApplicationAndOrdering() {
		MovesetBuilder builder = baseMoveset();
		PlayerPowerMovesetExtensions.applyRegisteredExtensions(
				ORDERED_TARGET, builder);
		PlayerPowerMovesetExtensions.applyRegisteredExtensions(
				ORDERED_TARGET, builder);
		assertOrderedBuilder(builder);
	}

	private static void verifyPlayerPowerTypeLifecycle() {
		TestPlayerPowerType powerType =
				new TestPlayerPowerType(
						ORDERED_TARGET, baseMoveset());

		assertOrderedMoveset(powerType.makeMoveset(null));
		assertOrderedMoveset(powerType.makeMoveset(null));
		assertOrderedMoveset(powerType.getBaseMoveset());
		assertOrderedHotbar(
				powerType.makeDefaultControlSchemeTemplate());

		JsonObject config = new JsonObject();
		JsonObject moveset = new JsonObject();
		moveset.add("abilities", new JsonObject());
		config.add("moveset", moveset);
		powerType.applyConfig(config);
		assertOrderedMoveset(powerType.makeMoveset(null));
		assertOrderedHotbar(
				powerType.makeDefaultControlSchemeTemplate());

		powerType.restoreDefaults();
		assertOrderedMoveset(powerType.getBaseMoveset());
		assertOrderedHotbar(
				powerType.makeDefaultControlSchemeTemplate());
	}

	private static void verifyHotbarSlotVariation() {
		ResourceLocation target =
				id("rotp_test", "variation_target");
		AbilityType<Ability> variationType =
				abilityType("blood_refill");
		PlayerPowerMovesetExtensions.register(
				PlayerPowerMovesetExtensions.builder(
								target,
								id("rotp_test", "variation_extension"),
								100)
						.addAbility(
								"blood_refill",
								variationType.registryKey,
								() -> variationType)
						.addHotbarSlotVariation(
								"default",
								0,
								"anchor",
								"blood_refill",
								InputKey.Modifier.SHIFT,
								InputMethod.HOLD)
						.build());

		TestPlayerPowerType powerType =
				new TestPlayerPowerType(target, baseMoveset());
		Moveset moveset = powerType.makeMoveset(null);
		check(new ArrayList<>(moveset.abilities.keySet())
						.equals(List.of(
								"anchor",
								"tail",
								"blood_refill")),
				"variation ability registration drifted");

		ControlSchemeTemplate controls =
				powerType.makeDefaultControlSchemeTemplate();
		List<Map<InputKey.Modifier, Map<InputMethod, String>>> slots =
				controls.defaultGroup.hotbars.get(0).slots;
		check(slots.size() == 2,
				"slot variation must not create a standalone slot");
		check("anchor".equals(
						slots.get(0).get(null)
								.get(InputMethod.CLICK)),
				"base hotbar entry drifted");
		check("blood_refill".equals(
						slots.get(0)
								.get(InputKey.Modifier.SHIFT)
								.get(InputMethod.HOLD)),
				"shift-hold variation was not attached");
	}

	private static void verifyLateRegistrationRefreshesCaches() {
		ResourceLocation target =
				id("rotp_test", "late_player_power");
		TestPlayerPowerType powerType =
				new TestPlayerPowerType(target, baseMoveset());
		check(new ArrayList<>(
						powerType.getBaseMoveset().abilities.keySet())
						.equals(List.of("anchor", "tail")),
				"unregistered extension altered cached moveset");

		AbilityType<Ability> lateType = abilityType("late");
		PlayerPowerMovesetExtensions.register(
				orderedExtension(
						target,
						id("rotp_test", "late_extension"),
						0,
						List.of("late"),
						List.of(lateType)));

		check(new ArrayList<>(
						powerType.getBaseMoveset().abilities.keySet())
						.equals(List.of(
								"anchor", "tail", "late")),
				"late registration did not refresh base moveset");
		assertHotbarAbilities(
				powerType.makeDefaultControlSchemeTemplate(),
				List.of("anchor", "late", "tail"));
	}

	private static void verifyNoCrossPowerLeakage() {
		TestPlayerPowerType unrelated =
				new TestPlayerPowerType(
						id("rotp_test", "unrelated_player_power"),
						baseMoveset());
		check(new ArrayList<>(
						unrelated.makeMoveset(null)
								.abilities.keySet())
						.equals(List.of("anchor", "tail")),
				"PlayerPower extension leaked to another target");
		assertHotbarAbilities(
				unrelated.makeDefaultControlSchemeTemplate(),
				List.of("anchor", "tail"));
	}

	private static void verifyMissingReferencesAndConflictsFailFast() {
		AbilityType<Ability> missingSchemeType =
				abilityType("missing_scheme");
		ResourceLocation missingSchemeTarget =
				id("rotp_test", "missing_scheme_target");
		PlayerPowerMovesetExtensions.register(
				PlayerPowerMovesetExtensions.builder(
								missingSchemeTarget,
								id("rotp_test", "missing_scheme_extension"),
								0)
						.addAbility(
								"missing_scheme",
								missingSchemeType.registryKey,
								() -> missingSchemeType)
						.insertAfterInHotbar(
								"absent",
								0,
								"anchor",
								"missing_scheme",
								InputMethod.HOLD)
						.build());
		expectFailure(
				() -> new TestPlayerPowerType(
						missingSchemeTarget, baseMoveset())
						.makeMoveset(null),
				"control scheme does not exist: absent");

		AbilityType<Ability> missingHotbarType =
				abilityType("missing_hotbar");
		ResourceLocation missingHotbarTarget =
				id("rotp_test", "missing_hotbar_target");
		PlayerPowerMovesetExtensions.register(
				PlayerPowerMovesetExtensions.builder(
								missingHotbarTarget,
								id("rotp_test", "missing_hotbar_extension"),
								0)
						.addAbility(
								"missing_hotbar",
								missingHotbarType.registryKey,
								() -> missingHotbarType)
						.insertAfterInHotbar(
								"default",
								9,
								"anchor",
								"missing_hotbar",
								InputMethod.HOLD)
						.build());
		expectFailure(
				() -> new TestPlayerPowerType(
						missingHotbarTarget, baseMoveset())
						.makeMoveset(null),
				"hotbar does not exist: 9");

		AbilityType<Ability> missingAnchorType =
				abilityType("missing_anchor");
		ResourceLocation missingAnchorTarget =
				id("rotp_test", "missing_anchor_target");
		PlayerPowerMovesetExtensions.register(
				PlayerPowerMovesetExtensions.builder(
								missingAnchorTarget,
								id("rotp_test", "missing_anchor_extension"),
								0)
						.addAbility(
								"missing_anchor",
								missingAnchorType.registryKey,
								() -> missingAnchorType)
						.insertAfterInHotbar(
								"default",
								0,
								"absent",
								"missing_anchor",
								InputMethod.HOLD)
						.build());
		expectFailure(
				() -> new TestPlayerPowerType(
						missingAnchorTarget, baseMoveset())
						.makeMoveset(null),
				"hotbar entry references missing ability: absent");

		ResourceLocation missingAbilityTarget =
				id("rotp_test", "missing_ability_target");
		PlayerPowerMovesetExtensions.register(
				PlayerPowerMovesetExtensions.builder(
								missingAbilityTarget,
								id("rotp_test", "missing_ability_extension"),
								0)
						.insertAfterInHotbar(
								"default",
								0,
								"anchor",
								"absent",
								InputMethod.HOLD)
						.build());
		expectFailure(
				() -> new TestPlayerPowerType(
						missingAbilityTarget, baseMoveset())
						.makeMoveset(null),
				"hotbar entry references missing ability: absent");

		AbilityType<Ability> actualType =
				abilityType("actual_type");
		ResourceLocation mismatchTarget =
				id("rotp_test", "mismatch_target");
		PlayerPowerMovesetExtensions.register(
				PlayerPowerMovesetExtensions.builder(
								mismatchTarget,
								id("rotp_test", "mismatch_extension"),
								0)
						.addAbility(
								"mismatch",
								id("rotp_test", "expected_type"),
								() -> actualType)
						.build());
		expectFailure(
				() -> new TestPlayerPowerType(
						mismatchTarget, baseMoveset())
						.makeMoveset(null),
				"ability type ID mismatch");

		AbilityType<Ability> collisionType =
				abilityType("collision");
		ResourceLocation collisionTarget =
				id("rotp_test", "variation_collision_target");
		PlayerPowerMovesetExtensions.register(
				PlayerPowerMovesetExtensions.builder(
								collisionTarget,
								id("rotp_test", "collision_extension"),
								0)
						.addAbility(
								"collision",
								collisionType.registryKey,
								() -> collisionType)
						.addHotbarSlotVariation(
								"default",
								0,
								"anchor",
								"collision",
								InputKey.Modifier.SHIFT,
								InputMethod.HOLD)
						.build());
		expectFailure(
				() -> new TestPlayerPowerType(
						collisionTarget,
						baseMovesetWithShiftVariation())
						.makeMoveset(null),
				"hotbar variation already exists");
	}

	private static PlayerPowerMovesetExtensions.Extension
			orderedExtension(
					ResourceLocation target,
					ResourceLocation extensionId,
					int order,
					List<String> abilityNames,
					List<AbilityType<Ability>> abilityTypes) {
		PlayerPowerMovesetExtensions.Builder builder =
				PlayerPowerMovesetExtensions.builder(
						target, extensionId, order);
		for (int i = 0; i < abilityNames.size(); i++) {
			String abilityName = abilityNames.get(i);
			AbilityType<Ability> abilityType =
					abilityTypes.get(i);
			builder.addAbility(
					abilityName,
					abilityType.registryKey,
					() -> abilityType);
			builder.insertAfterInHotbar(
					"default",
					0,
					"anchor",
					abilityName,
					InputMethod.HOLD);
		}
		return builder.build();
	}

	private static MovesetBuilder baseMoveset() {
		return new MovesetBuilder()
				.addAbility("anchor", ANCHOR_TYPE)
				.addAbility("tail", TAIL_TYPE)
				.makeControlScheme("default")
					.makeHotbar(0, InputKey.RMB, InputKey.Q)
					.addToHotbar(
							"anchor", 0, InputMethod.CLICK)
					.addToHotbar(
							"tail", 0, InputMethod.HOLD)
				.finalizeControlScheme();
	}

	private static MovesetBuilder
			baseMovesetWithShiftVariation() {
		AbilityType<Ability> occupiedType =
				abilityType("occupied");
		return new MovesetBuilder()
				.addAbility("anchor", ANCHOR_TYPE)
				.addAbility("tail", TAIL_TYPE)
				.addAbility("occupied", occupiedType)
				.makeControlScheme("default")
					.makeHotbar(0, InputKey.RMB, InputKey.Q)
					.addToHotbar(
							"anchor", 0, InputMethod.CLICK)
					.addHotbarSlotVariation(
							"occupied",
							"anchor",
							InputKey.Modifier.SHIFT,
							InputMethod.HOLD)
					.addToHotbar(
							"tail", 0, InputMethod.HOLD)
				.finalizeControlScheme();
	}

	private static void assertOrderedBuilder(
			MovesetBuilder builder) {
		check(new ArrayList<>(builder.abilities.keySet())
						.equals(List.of(
								"anchor",
								"tail",
								"low_a",
								"low_b",
								"alpha",
								"zeta")),
				"ability application order drifted");
		assertOrderedHotbar(
				builder.controlSchemes.get("default"));
	}

	private static void assertOrderedMoveset(
			Moveset moveset) {
		check(new ArrayList<>(moveset.abilities.keySet())
						.equals(List.of(
								"anchor",
								"tail",
								"low_a",
								"low_b",
								"alpha",
								"zeta")),
				"PlayerPower moveset lifecycle reordered abilities");
	}

	private static void assertOrderedHotbar(
			ControlSchemeTemplate controls) {
		assertHotbarAbilities(
				controls,
				List.of(
						"anchor",
						"low_a",
						"low_b",
						"alpha",
						"zeta",
						"tail"));
	}

	private static void assertHotbarAbilities(
			ControlSchemeTemplate controls,
			List<String> expected) {
		check(controls != null,
				"expected control scheme is missing");
		check(controls.defaultGroup.hotbars.size() == 1,
				"extension created an unexpected hotbar");
		List<Map<InputKey.Modifier, Map<InputMethod, String>>> slots =
				controls.defaultGroup.hotbars.get(0).slots;
		check(slots.size() == expected.size(),
				"hotbar slot count drifted");
		List<String> actual = new ArrayList<>();
		for (Map<InputKey.Modifier, Map<InputMethod, String>> slot
				: slots) {
			Map<InputMethod, String> base = slot.get(null);
			check(base != null && base.size() == 1,
					"hotbar base slot shape drifted");
			actual.add(base.values().iterator().next());
		}
		check(actual.equals(expected),
				"hotbar order drifted: " + actual);
	}

	private static void verifyRestrictedBuilderSurface() {
		for (Method method
				: PlayerPowerMovesetExtensions.Builder.class
						.getMethods()) {
			check(method.getReturnType() != MovesetBuilder.class,
					"public extension builder exposes MovesetBuilder");
			check(method.getReturnType()
							!= ControlSchemeTemplate.class,
					"public extension builder exposes controls");
			for (Class<?> parameter
					: method.getParameterTypes()) {
				check(parameter != MovesetBuilder.class,
						"public extension builder accepts MovesetBuilder");
				check(parameter
								!= ControlSchemeTemplate.class,
						"public extension builder accepts controls");
			}
		}
	}

	private static AbilityType<Ability> abilityType(
			String path) {
		return new AbilityType<>(
				id("rotp_test", path), Ability::new);
	}

	private static ResourceLocation id(
			String namespace, String path) {
		return ResourceLocation.fromNamespaceAndPath(
				namespace, path);
	}

	private static void expectFailure(
			Runnable action, String expectedMessage) {
		try {
			action.run();
			throw new AssertionError(
					"Expected failure containing: "
							+ expectedMessage);
		}
		catch (IllegalStateException expected) {
			check(expected.getMessage().contains(expectedMessage),
					"unexpected failure: "
							+ expected.getMessage());
		}
	}

	private static void check(
			boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static final class TestPlayerPowerType
			extends PlayerPowerType<PlayerPowerData> {
		private TestPlayerPowerType(
				ResourceLocation registryKey,
				MovesetBuilder moveset) {
			super(registryKey, moveset);
		}

		@Override
		public PlayerPowerData newDataInstance() {
			return null;
		}

		@Override
		public PowerClass<PlayerPower> getPowerClass() {
			return null;
		}
	}
}
