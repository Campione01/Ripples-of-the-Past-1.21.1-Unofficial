package com.github.standobyte.jojo.api.healing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import net.minecraft.resources.ResourceLocation;

public final class CrazyDiamondRestoreExtensionsSmokeTest {
	private CrazyDiamondRestoreExtensionsSmokeTest() {}

	public static void run() {
		CrazyDiamondRestoreExtensions.resetForTests();
		ResourceLocation first = id("first");
		ResourceLocation second = id("second");
		CrazyDiamondRestoreExtensions.register(
				first, new CrazyDiamondRestoreExtension() {});
		CrazyDiamondRestoreExtensions.register(
				second, new CrazyDiamondRestoreExtension() {});
		check(CrazyDiamondRestoreExtensions.registeredOwners()
						.equals(List.of(first, second)),
				"Crazy Diamond extension order changed");
		expectIllegalState(() ->
				CrazyDiamondRestoreExtensions.register(
						first,
						new CrazyDiamondRestoreExtension() {}));

		check(!ExternalRestoreResult.unhandled().handled(),
				"default external result must be unhandled");
		check(RestoreAugmentation.none().hpForExperience() == 0,
				"default living augmentation changed");

		Path root = Path.of(System.getProperty("user.dir"));
		String ability = read(root.resolve(
				"src/main/java/com/github/standobyte/jojoimpl/stands/"
				+ "crazydiamond/CrazyDHealAbility.java"));
		for (String token : new String[] {
			"CrazyDiamondRestoreExtensions",
			".canTarget(target)",
			".restoreExternal(",
			".afterLivingRestoreAttempt(",
			"if (!level.isClientSide() && user != null)",
			"augmentation.hpForExperience()"
		}) {
			check(ability.contains(token),
					"Crazy Diamond integration missing: " + token);
		}
		CrazyDiamondRestoreExtensions.resetForTests();
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
				"duplicate Crazy Diamond owner was accepted");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
