package com.github.standobyte.jojo.powersystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.github.standobyte.jojo.client.input.InputHandler;
import com.github.standobyte.jojo.client.input.VanillaKeybinds;
import com.github.standobyte.jojo.powersystem.ability.controls.InputUseVanillaMapping;

public final class SharedGrabChargedHeavyInputSmokeTest {
	private static final String KEY_MAPPING_NAME = "jojo_ripples.key.grab_charged_heavy";
	private static final List<String> CHARGED_HEAVY_STANDS = List.of(
			"StandInitStarPlatinum.java",
			"StandInitTheWorld.java",
			"StandInitCrazyDiamond.java",
			"StandInitGoldExperience.java",
			"StandInitMagiciansRed.java");
	private static final List<String> GRAB_STANDS = List.of(
			"StandInitStarPlatinum.java",
			"StandInitTheWorld.java",
			"StandInitCrazyDiamond.java");

	private SharedGrabChargedHeavyInputSmokeTest() {}

	public static void run() {
		check(MovesetBuilder.DEFAULT_GRAB_INPUT
				== MovesetBuilder.DEFAULT_CHARGED_HEAVY_INPUT,
				"grab and charged heavy must share one rebindable input object");
		check(MovesetBuilder.DEFAULT_GRAB_INPUT instanceof InputUseVanillaMapping,
				"the shared combat input must be a vanilla KeyMapping reference");
		check(KEY_MAPPING_NAME.equals(
				((InputUseVanillaMapping) MovesetBuilder.DEFAULT_GRAB_INPUT).keyMappingName),
				"unexpected shared combat KeyMapping name");

		Path root = Path.of(System.getProperty("user.dir"));
		verifyUnsummonedStandVanillaUseOwnership(root);
		String vanillaKeybinds = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/client/input/VanillaKeybinds.java"));
		check(vanillaKeybinds.contains(
				"MovesetBuilder.GRAB_CHARGED_HEAVY_KEY_MAPPING_NAME"),
				"shared combat KeyMapping is not registered");
		check(vanillaKeybinds.contains("KeyModifier.SHIFT")
				&& vanillaKeybinds.contains("InputConstants.MOUSE_BUTTON_RIGHT"),
				"shared combat KeyMapping must default to Shift + RMB");

		Path stands = root.resolve(
				"src/main/java/com/github/standobyte/jojoimpl/stands");
		for (String file : CHARGED_HEAVY_STANDS) {
			String source = read(stands.resolve(file));
			check(count(source,
					".bind(\"heavy_charged\", InputMethod.HOLD, MovesetBuilder.DEFAULT_CHARGED_HEAVY_INPUT)") == 2,
					file + " must bind charged heavy to the shared input in both schemes");
		}
		for (String file : GRAB_STANDS) {
			String source = read(stands.resolve(file));
			check(count(source,
					".bind(\"grab\", InputMethod.CLICK, MovesetBuilder.DEFAULT_GRAB_INPUT)") == 2,
					file + " must bind grab click to the shared input in both schemes");
		}

		String english = read(root.resolve(
				"src/main/resources/assets/jojo_ripples/lang/en_us.json"));
		String chinese = read(root.resolve(
				"src/main/resources/assets/jojo_ripples/lang/zh_cn.json"));
		check(english.contains("\"jojo_ripples.skill.grab.controls\": \"Tap %s\"")
				&& english.contains("\"jojo_ripples.skill.heavy_charged.controls\": \"Hold %s\""),
				"English skill controls must display the current shared binding");
		check(chinese.contains("\"jojo_ripples.skill.grab.controls\": \"短按 %s\"")
				&& chinese.contains("\"jojo_ripples.skill.heavy_charged.controls\": \"长按 %s\""),
				"Chinese skill controls must display the current shared binding");
	}

	private static void verifyUnsummonedStandVanillaUseOwnership(Path root) {
		check(InputHandler.shouldPreserveUnsummonedStandVanillaUsePress(
				true, true, true, false, false, false),
				"HUD-active unsummoned Stand must leave Shift+Use to vanilla");
		check(InputHandler.shouldPreserveUnsummonedStandVanillaUsePress(
				true, true, true, true, false, false),
				"semantic remapped Use must fail closed without a synchronized live Stand");
		check(!InputHandler.shouldPreserveUnsummonedStandVanillaUsePress(
				true, true, true, true, true, false),
				"a live ready Stand must retain its Shift+Use ability binding");
		check(InputHandler.shouldPreserveUnsummonedStandVanillaUsePress(
				true, true, true, true, true, true),
				"full-body unsummoning Stand must leave Shift+Use to vanilla");
		check(!InputHandler.shouldPreserveUnsummonedStandVanillaUsePress(
				false, true, true, false, false, false),
				"HUD-inactive input must not be claimed by the Stand-specific guard");
		check(!InputHandler.shouldPreserveUnsummonedStandVanillaUsePress(
				true, true, false, false, false, false),
				"plain Use must remain outside the Shift+Use guard");
		check(!InputHandler.shouldPreserveUnsummonedStandVanillaUsePress(
				true, false, true, false, false, false),
				"non-Use Shift input must remain outside the vanilla Use guard");

		boolean unsummonedStandShiftUse =
				InputHandler.shouldPreserveUnsummonedStandVanillaUsePress(
						true, true, true, false, false, false);
		check(!VanillaKeybinds
				.shouldActivateGrabChargedHeavyConflictContext(
						true, unsummonedStandShiftUse),
				"unsummoned Stand Shift+Use must deactivate the combat mapping context");
		boolean liveStandShiftUse =
				InputHandler.shouldPreserveUnsummonedStandVanillaUsePress(
						true, true, true, true, true, false);
		check(VanillaKeybinds
				.shouldActivateGrabChargedHeavyConflictContext(
						true, liveStandShiftUse),
				"live ready Stand Shift+Use must retain the combat mapping context");
		boolean hamonShiftUse =
				InputHandler.shouldPreserveUnsummonedStandVanillaUsePress(
						false, true, true, false, false, false);
		check(VanillaKeybinds
				.shouldActivateGrabChargedHeavyConflictContext(
						true, hamonShiftUse),
				"Hamon HUD Shift+Use must not deactivate the combat mapping context");
		boolean standUseWithoutShift =
				InputHandler.shouldPreserveUnsummonedStandVanillaUsePress(
						true, true, false, false, false, false);
		check(VanillaKeybinds
				.shouldActivateGrabChargedHeavyConflictContext(
						true, standUseWithoutShift),
				"Use without Shift must not deactivate the combat mapping context");
		check(VanillaKeybinds
				.shouldActivateGrabChargedHeavyConflictContext(true, false),
				"ordinary in-game input must retain the combat mapping context");
		check(!VanillaKeybinds
				.shouldActivateGrabChargedHeavyConflictContext(false, false),
				"combat mapping context must remain inactive outside IN_GAME");

		String inputHandler = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/client/input/InputHandler.java"));
		check(inputHandler.contains(
				"key.equals(ClientKey.fromVanillaKeybind(mc.options.keyUse))"),
				"raw input guard must follow the configured vanilla Use mapping");
		int schemeSelector = inputHandler.indexOf(
				"private ClientControlScheme getActiveControlSchemeForInput");
		int activeScheme = inputHandler.indexOf(
				"ClientControlScheme activeControlScheme = getActiveControlScheme();",
				schemeSelector);
		int preserveGuard = inputHandler.indexOf(
				"shouldPreserveUnsummonedStandVanillaUsePress(",
				activeScheme);
		int directPlayerPower = inputHandler.indexOf(
				"getDirectPlayerPowerControlScheme(key, keyModifier)",
				preserveGuard);
		check(schemeSelector >= 0 && activeScheme > schemeSelector
				&& preserveGuard > activeScheme
				&& directPlayerPower > preserveGuard,
				"Stand HUD Shift+Use guard must run before same-key direct Hamon arbitration");
		int pressCase = inputHandler.indexOf("case InputConstants.PRESS -> {");
		int releaseCase = inputHandler.indexOf("case InputConstants.RELEASE -> {");
		int repeatCase = inputHandler.indexOf("case InputConstants.REPEAT -> {");
		int pressSelector = inputHandler.indexOf(
				"getActiveControlSchemeForInput(key, keyModifier)", pressCase);
		check(pressCase >= 0 && pressSelector > pressCase
				&& releaseCase > pressSelector,
				"unsummoned Stand ownership guard must only admit new presses");
		String releaseBlock = inputHandler.substring(releaseCase, repeatCase);
		check(releaseBlock.contains("doReleaseInput(keyId);")
				&& releaseBlock.contains("removeHeldKeyTimer(key);"),
				"release after Stand disappearance must drain the existing held timer");

		String vanillaClickInput = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/client/input/StandVanillaClickInput.java"));
		int semanticGuard = vanillaClickInput.indexOf(
				"inputHandler.shouldPreserveSemanticVanillaUsePress()");
		int mappedAbilityInput = vanillaClickInput.indexOf(
				"handleVanillaMappedAbilityInput(event, keyCode)");
		check(semanticGuard >= 0 && mappedAbilityInput > semanticGuard,
				"semantic Use must be guarded before hardcoded mouse ability translation");

		String vanillaKeybinds = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/client/input/VanillaKeybinds.java"));
		check(vanillaKeybinds.contains(
				"GRAB_CHARGED_HEAVY_CONFLICT_CONTEXT, KeyModifier.SHIFT"),
				"shared combat mapping must use its modifier-aware conflict context");
		check(vanillaKeybinds.contains(
				"return KeyConflictContext.IN_GAME.conflicts("),
				"shared combat conflicts must delegate to IN_GAME semantics");
	}

	private static String read(Path path) {
		try {
			return Files.readString(path);
		}
		catch (IOException e) {
			throw new AssertionError("failed to read " + path, e);
		}
	}

	private static int count(String source, String token) {
		int count = 0;
		int index = 0;
		while ((index = source.indexOf(token, index)) >= 0) {
			count++;
			index += token.length();
		}
		return count;
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
