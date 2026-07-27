package com.github.standobyte.jojo.powersystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
