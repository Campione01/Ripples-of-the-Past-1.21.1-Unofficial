package com.github.standobyte.jojo.api.item;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import net.minecraft.resources.ResourceLocation;

public final class ItemHandFreePredicatesSmokeTest {
	private ItemHandFreePredicatesSmokeTest() {}

	public static void run() {
		ItemHandFreePredicates.resetForTests();
		ResourceLocation failingOwner = id("failing");
		ResourceLocation grantingOwner = id("plain_item");
		ItemHandFreePredicates.register(
				failingOwner,
				stack -> {
					throw new IllegalStateException(
							"isolated test failure");
				});
		ItemHandFreePredicates.register(
				grantingOwner,
				stack -> true);
		check(ItemHandFreePredicates.registeredOwners().equals(
				List.of(failingOwner, grantingOwner)),
				"hand-free predicate owners lost registration order");

		boolean duplicateRejected = false;
		try {
			ItemHandFreePredicates.register(
					id("plain_item"), stack -> false);
		}
		catch (IllegalStateException expected) {
			duplicateRejected = true;
		}
		check(duplicateRejected,
				"duplicate hand-free owner must be rejected");

		ItemHandFreePredicates.resetForTests();
		check(ItemHandFreePredicates.registeredOwners().isEmpty(),
				"registry reset did not remove test predicates");
		verifyDefaultAndIsolationContract();
	}

	private static void verifyDefaultAndIsolationContract() {
		Path root = Path.of(System.getProperty("user.dir"));
		String utility = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/"
				+ "util/functions/UtilFunctions.java"));
		check(utility.contains("stack.isEmpty()")
				&& utility.contains("instanceof GlovesItem gloves")
				&& utility.contains("gloves.openFingers()")
				&& utility.contains(
						"ItemHandFreePredicates.matches(stack)"),
				"itemHandFree defaults or addon dispatch drifted");

		String registry = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/api/"
				+ "item/ItemHandFreePredicates.java"));
		check(registry.contains(
				"catch (RuntimeException error)")
				&& registry.contains(
						"Item hand-free predicate {} failed.")
				&& registry.contains("\t\treturn false;"),
				"predicate failures must be isolated and fail closed");
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

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
