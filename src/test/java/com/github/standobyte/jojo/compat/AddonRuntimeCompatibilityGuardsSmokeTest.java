package com.github.standobyte.jojo.compat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AddonRuntimeCompatibilityGuardsSmokeTest {
	private AddonRuntimeCompatibilityGuardsSmokeTest() {}

	public static void main(String[] args) {
		Path root = Path.of(System.getProperty("user.dir"));
		Path main = root.resolve("src/main/java");

		String model = read(main.resolve(
				"com/github/standobyte/jojo/client/entityrender/stand/"
						+ "StandEntityModel.java"));
		check(model.contains(
				"addMissingItemHoldPoint(left_arm_bend, \"left_item\")"),
				"left-hand attachment must use the resolved bend descendant");
		check(model.contains(
				"addMissingItemHoldPoint(right_arm_bend, \"right_item\")"),
				"right-hand attachment must use the resolved bend descendant");
		check(!model.contains(
				"left_arm.getChild(\"left_arm_bend\")"),
				"addon models must not require bend bones to be direct children");
		check(!model.contains(
				"right_arm.getChild(\"right_arm_bend\")"),
				"addon models must not require bend bones to be direct children");

		String actions = read(main.resolve(
				"com/github/standobyte/jojo/powersystem/entityaction/"
						+ "LivingComponentAction.java"));
		check(actions.contains(
				"EntityActionInstance tickingAction = action;"),
				"entity-action ticks must retain the exact instance being ticked");
		check(actions.contains(
				"if (action == tickingAction && tickingAction.isOver())"),
				"reentrant action replacement or clearing must be handled safely");

		System.out.println("Addon runtime compatibility guards: PASS");
	}

	private static String read(Path path) {
		try {
			return Files.readString(path);
		}
		catch (IOException exception) {
			throw new AssertionError("failed to read " + path, exception);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
