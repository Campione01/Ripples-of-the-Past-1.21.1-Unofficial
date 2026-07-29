package com.github.standobyte.jojo.api.item;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import net.minecraft.resources.ResourceLocation;

public final class LivingHandUseBlockersSmokeTest {
	private LivingHandUseBlockersSmokeTest() {}

	public static void run() {
		LivingHandUseBlockers.resetForTests();
		ResourceLocation first = id("first");
		ResourceLocation second = id("second");
		LivingHandUseBlockers.register(
				first, (entity, hand) -> false);
		LivingHandUseBlockers.register(
				second, (entity, hand) -> true);
		check(LivingHandUseBlockers.registeredOwners()
						.equals(List.of(first, second)),
				"living hand blocker order changed");
		expectIllegalState(() ->
				LivingHandUseBlockers.register(
						first, (entity, hand) -> false));

		Path root = Path.of(System.getProperty("user.dir"));
		String util = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/util/"
				+ "functions/UtilFunctions.java"));
		check(util.contains("boolean isHandFree(")
						&& util.contains(
								"&& !LivingHandUseBlockers.isBlocked(")
						&& util.contains("boolean areHandsFree(")
						&& util.contains(
								"for (InteractionHand hand : hands)")
						&& util.contains("return false;"),
				"entity-aware all-hands semantics are incomplete");
		for (String file : List.of(
				"PillarmanHeavyPunchAbility.java",
				"PillarmanBladeSlashAbility.java",
				"PillarmanBladeBarrageAbility.java",
				"PillarmanAbsorptionAbility.java")) {
			String source = read(root.resolve(
					"src/main/java/com/github/standobyte/jojoimpl/"
					+ "powers/pillarman/abilities/" + file));
			check(source.contains("UtilFunctions.isHandFree(")
							&& source.contains(
									"InteractionHand.MAIN_HAND"),
					"entity-aware hand helper missing from " + file);
		}
		LivingHandUseBlockers.resetForTests();
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
				"duplicate living hand blocker owner was accepted");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
