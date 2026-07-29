package com.github.standobyte.jojo.api.healing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import net.minecraft.resources.ResourceLocation;

public final class GoldExperienceExternalHealingTargetsSmokeTest {
	private GoldExperienceExternalHealingTargetsSmokeTest() {}

	public static void run() {
		GoldExperienceExternalHealingTargets.resetForTests();
		ResourceLocation first = id("first");
		ResourceLocation second = id("second");
		GoldExperienceExternalHealingTargets.register(
				first, (rawTarget, healer) -> null);
		GoldExperienceExternalHealingTargets.register(
				second, (rawTarget, healer) -> null);
		check(GoldExperienceExternalHealingTargets.registeredOwners()
						.equals(List.of(first, second)),
				"Gold Experience handler order changed");
		expectIllegalState(() ->
				GoldExperienceExternalHealingTargets.register(
						first,
						(rawTarget, healer) -> null));

		Path root = Path.of(System.getProperty("user.dir"));
		String ability = read(root.resolve(
				"src/main/java/com/github/standobyte/jojoimpl/stands/"
				+ "goldexperience/GoldExperienceHealOtherAbility.java"));
		for (String token : new String[] {
			"ResolvedHealingTarget",
			"GoldExperienceExternalHealingTargets.resolve(",
			"resolved.rawTarget().getDisplayName()",
			"resolved.classificationOwner()",
			"resolved.healingTarget()",
			"GoldExperienceHealAbility.spendHealingMaterial(user)",
			"GoldExperienceHealAbility.applyGoldExperienceHeal("
		}) {
			check(ability.contains(token),
					"Gold Experience integration missing: " + token);
		}
		GoldExperienceExternalHealingTargets.resetForTests();
	}

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(
				"rotp_test", path);
	}

	private static String read(Path path) {
		try {
			return Files.readString(path);
		}
		catch (IOException error) {
			throw new AssertionError("failed to read " + path, error);
		}
	}

	private static void expectIllegalState(Runnable action) {
		try {
			action.run();
		}
		catch (IllegalStateException expected) {
			return;
		}
		throw new AssertionError(
				"duplicate Gold Experience owner was accepted");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
