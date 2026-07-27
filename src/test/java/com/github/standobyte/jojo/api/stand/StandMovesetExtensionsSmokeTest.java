package com.github.standobyte.jojo.api.stand;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.github.standobyte.jojo.api.stand.StandMovesetExtensions.StandSkill;
import com.github.standobyte.jojo.powersystem.Moveset;
import com.github.standobyte.jojo.powersystem.MovesetBuilder;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.PowerData;
import com.github.standobyte.jojo.powersystem.PowerType;
import com.github.standobyte.jojo.powersystem.ability.Ability;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.controls.ControlSchemeTemplate;
import com.github.standobyte.jojo.powersystem.ability.controls.InputKey;
import com.github.standobyte.jojo.powersystem.ability.controls.InputMethod;
import com.google.gson.JsonObject;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class StandMovesetExtensionsSmokeTest {
	private static final ResourceLocation TARGET =
			id("rotp_test", "ordered_target");
	private static final AbilityType<Ability> BASE_TYPE =
			abilityType("base");
	private static final AbilityType<Ability> LOW_TYPE =
			abilityType("low");
	private static final AbilityType<Ability> ALPHA_TYPE =
			abilityType("alpha");
	private static final AbilityType<Ability> ZETA_TYPE =
			abilityType("zeta");

	private StandMovesetExtensionsSmokeTest() {}

	public static void run() {
		verifyRestrictedBuilderSurface();
		registerOrderedExtensions();
		verifyDuplicateAndConflictRegistration();
		verifyRepeatedApplication();
		verifyPowerTypeLifecycle();
		verifyLateRegistrationRefreshesBaseMoveset();
		verifyMissingTargetIsInert();
		verifyMissingReferencesFailFast();
	}

	private static void registerOrderedExtensions() {
		StandMovesetExtensions.register(extension(
				TARGET, id("rotp_test", "zeta"), 20,
				"zeta", ZETA_TYPE));
		StandMovesetExtensions.register(extension(
				TARGET, id("rotp_test", "low"), 10,
				"low", LOW_TYPE));
		StandMovesetExtensions.register(extension(
				TARGET, id("rotp_test", "alpha"), 20,
				"alpha", ALPHA_TYPE));
	}

	private static void verifyDuplicateAndConflictRegistration() {
		StandMovesetExtensions.register(extension(
				TARGET, id("rotp_test", "alpha"), 20,
				"alpha", ALPHA_TYPE));

		expectFailure(
				() -> StandMovesetExtensions.register(extension(
						TARGET, id("rotp_test", "alpha"), 21,
						"alpha", ALPHA_TYPE)),
				"Conflicting Stand moveset extension definition");
	}

	private static void verifyRepeatedApplication() {
		MovesetBuilder builder = baseMoveset();
		StandMovesetExtensions.applyRegisteredExtensions(TARGET, builder);
		StandMovesetExtensions.applyRegisteredExtensions(TARGET, builder);
		assertBuilderState(builder);
	}

	private static void verifyPowerTypeLifecycle() {
		TestPowerType stand = powerType(TARGET, baseMoveset());

		assertMovesetState(stand.makeMoveset(null));
		assertMovesetState(stand.makeMoveset(null));
		assertMovesetState(stand.getBaseMoveset());
		assertControlSchemeState(
				stand.makeDefaultControlSchemeTemplate());
		check(new ArrayList<>(stand.getUnlockableSkills().keySet())
				.equals(List.of("low", "alpha", "zeta")),
				"extension skills must follow extension application order");

		JsonObject config = new JsonObject();
		JsonObject moveset = new JsonObject();
		moveset.add("abilities", new JsonObject());
		config.add("moveset", moveset);
		stand.applyConfig(config);
		assertMovesetState(stand.makeMoveset(null));
		assertControlSchemeState(
				stand.makeDefaultControlSchemeTemplate());

		stand.restoreDefaults();
		assertMovesetState(stand.makeMoveset(null));
		assertMovesetState(stand.getBaseMoveset());
		assertControlSchemeState(
				stand.makeDefaultControlSchemeTemplate());
	}

	private static void verifyLateRegistrationRefreshesBaseMoveset() {
		ResourceLocation target = id("rotp_test", "late_target");
		AbilityType<Ability> lateType = abilityType("late");
		TestPowerType stand = powerType(target, baseMoveset());
		check(new ArrayList<>(stand.getBaseMoveset().abilities.keySet())
				.equals(List.of("base")),
				"unregistered extension must not alter a cached moveset");

		StandMovesetExtensions.register(extension(
				target, id("rotp_test", "late_extension"), 0,
				"late", lateType));

		check(new ArrayList<>(stand.getBaseMoveset().abilities.keySet())
				.equals(List.of("base", "late")),
				"late registration must invalidate the cached base moveset");
		check(new ArrayList<>(stand.makeMoveset(null).abilities.keySet())
				.equals(List.of("base", "late")),
				"late registration must apply to per-user movesets");
	}

	private static void verifyMissingTargetIsInert() {
		ResourceLocation missingTarget =
				id("rotp_test", "not_registered_anywhere");
		StandMovesetExtensions.register(extension(
				missingTarget, id("rotp_test", "pending"), 0,
				"pending", abilityType("pending")));

		TestPowerType unrelated = powerType(
				id("rotp_test", "unrelated"), baseMoveset());
		Moveset moveset = unrelated.makeMoveset(null);
		check(new ArrayList<>(moveset.abilities.keySet())
				.equals(List.of("base")),
				"an extension for a missing target must remain inert");
	}

	private static void verifyMissingReferencesFailFast() {
		ResourceLocation missingSchemeTarget =
				id("rotp_test", "missing_scheme_target");
		AbilityType<Ability> missingSchemeType =
				abilityType("missing_scheme");
		StandMovesetExtensions.register(
				StandMovesetExtensions.builder(
						missingSchemeTarget,
						id("rotp_test", "missing_scheme_extension"),
						0)
				.addAbility(
						"missing_scheme",
						missingSchemeType.registryKey,
						() -> missingSchemeType)
				.appendToHotbar(
						"absent", 0, "missing_scheme",
						InputMethod.HOLD)
				.build());
		expectFailure(
				() -> powerType(
						missingSchemeTarget, baseMoveset())
						.makeMoveset(null),
				"control scheme does not exist: absent");

		ResourceLocation missingHotbarTarget =
				id("rotp_test", "missing_hotbar_target");
		AbilityType<Ability> missingHotbarType =
				abilityType("missing_hotbar");
		StandMovesetExtensions.register(
				StandMovesetExtensions.builder(
						missingHotbarTarget,
						id("rotp_test", "missing_hotbar_extension"),
						0)
				.addAbility(
						"missing_hotbar",
						missingHotbarType.registryKey,
						() -> missingHotbarType)
				.appendToHotbar(
						"hotbar", 9, "missing_hotbar",
						InputMethod.HOLD)
				.build());
		expectFailure(
				() -> powerType(
						missingHotbarTarget, baseMoveset())
						.makeMoveset(null),
				"hotbar does not exist: 9");

		ResourceLocation missingAbilityTarget =
				id("rotp_test", "missing_ability_target");
		StandMovesetExtensions.register(
				StandMovesetExtensions.builder(
						missingAbilityTarget,
						id("rotp_test", "missing_ability_extension"),
						0)
				.appendToHotbar(
						"hotbar", 0, "absent",
						InputMethod.CLICK)
				.build());
		expectFailure(
				() -> powerType(
						missingAbilityTarget, baseMoveset())
						.makeMoveset(null),
				"hotbar entry references missing ability: absent");
	}

	private static StandMovesetExtensions.Extension extension(
			ResourceLocation target, ResourceLocation extensionId,
			int order, String abilityName,
			AbilityType<Ability> abilityType) {
		return StandMovesetExtensions.builder(
					target, extensionId, order)
				.addAbility(
						abilityName,
						abilityType.registryKey,
						() -> abilityType)
				.addSkill(StandSkill.startingAbility(abilityName))
				.appendToHotbar(
						"hotbar", 0, abilityName, InputMethod.HOLD)
				.build();
	}

	private static MovesetBuilder baseMoveset() {
		return new MovesetBuilder()
				.addAbility("base", BASE_TYPE)
				.makeControlScheme("hotbar")
					.makeHotbar(0, InputKey.RMB, InputKey.Q)
					.addToHotbar("base", 0, InputMethod.CLICK)
				.finalizeControlScheme();
	}

	private static TestPowerType powerType(
			ResourceLocation id, MovesetBuilder moveset) {
		return new TestPowerType(moveset, id);
	}

	private static void assertBuilderState(MovesetBuilder builder) {
		check(new ArrayList<>(builder.abilities.keySet()).equals(
				List.of("base", "low", "alpha", "zeta")),
				"direct application order or idempotence drifted");
		check(new ArrayList<>(builder.unlockableSkills.keySet()).equals(
				List.of("low", "alpha", "zeta")),
				"direct skill order or idempotence drifted");
		assertHotbar(
				builder.controlSchemes.get("hotbar"),
				List.of("base", "low", "alpha", "zeta"));
	}

	private static void assertMovesetState(Moveset moveset) {
		check(new ArrayList<>(moveset.abilities.keySet()).equals(
				List.of("base", "low", "alpha", "zeta")),
				"moveset lifecycle duplicated or reordered extensions");
	}

	private static void assertControlSchemeState(
			ControlSchemeTemplate controlScheme) {
		assertHotbar(
				controlScheme,
				List.of("base", "low", "alpha", "zeta"));
	}

	private static void assertHotbar(
			ControlSchemeTemplate controlScheme,
			List<String> expectedAbilities) {
		check(controlScheme != null,
				"expected named control scheme is missing");
		check(controlScheme.defaultGroup.hotbars.size() == 1,
				"extension must not create an extra hotbar");
		List<Map<InputKey.Modifier, Map<InputMethod, String>>> slots =
				controlScheme.defaultGroup.hotbars.get(0).slots;
		check(slots.size() == expectedAbilities.size(),
				"hotbar entry count drifted");
		List<String> actualAbilities = new ArrayList<>();
		for (Map<InputKey.Modifier, Map<InputMethod, String>> slot : slots) {
			Map<InputMethod, String> baseVariation = slot.get(null);
			check(baseVariation != null && baseVariation.size() == 1,
					"extension hotbar slot shape drifted");
			actualAbilities.add(baseVariation.values().iterator().next());
		}
		check(actualAbilities.equals(expectedAbilities),
				"extension hotbar order drifted: " + actualAbilities);
	}

	private static void verifyRestrictedBuilderSurface() {
		for (Method method :
				StandMovesetExtensions.Builder.class.getMethods()) {
			check(method.getReturnType() != MovesetBuilder.class,
					"public extension builder must not expose MovesetBuilder");
			check(method.getReturnType() != ControlSchemeTemplate.class,
					"public extension builder must not expose control schemes");
			for (Class<?> parameter : method.getParameterTypes()) {
				check(parameter != MovesetBuilder.class,
						"public extension builder must not accept MovesetBuilder");
				check(parameter != ControlSchemeTemplate.class,
						"public extension builder must not accept control schemes");
			}
		}
	}

	private static AbilityType<Ability> abilityType(String path) {
		return new AbilityType<>(
				id("rotp_test", path), Ability::new);
	}

	private static ResourceLocation id(String namespace, String path) {
		return ResourceLocation.fromNamespaceAndPath(namespace, path);
	}

	private static void expectFailure(
			Runnable action, String expectedMessage) {
		try {
			action.run();
			throw new AssertionError(
					"Expected failure containing: " + expectedMessage);
		}
		catch (IllegalStateException expected) {
			check(expected.getMessage().contains(expectedMessage),
					"unexpected failure: " + expected.getMessage());
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static final class TestPowerType extends PowerType {
		private final ResourceLocation id;

		private TestPowerType(
				MovesetBuilder moveset, ResourceLocation id) {
			super(moveset);
			this.id = id;
		}

		@Override
		public PowerData newDataInstance() {
			return null;
		}

		@Override
		public ResourceLocation getId() {
			return id;
		}

		@Override
		public PowerClass<?> getPowerClass() {
			return null;
		}

		@Override
		public Component getName(Power<?> power) {
			return null;
		}

		@Override
		protected ResourceLocation getMovesetExtensionTargetId() {
			return id;
		}
	}
}
