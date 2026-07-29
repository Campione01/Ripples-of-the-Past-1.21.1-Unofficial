package com.github.standobyte.jojo.api.client.vampirism;

import java.util.Set;

import net.minecraft.resources.ResourceLocation;

public final class HungryZombiePoseProvidersSmokeTest {
	private HungryZombiePoseProvidersSmokeTest() {}

	public static void run() {
		ResourceLocation owner =
				ResourceLocation.fromNamespaceAndPath(
						"rotp_test", "hungry_zombie_pose");

		HungryZombiePoseProviders.resetForTests();
		HungryZombiePoseProviders.register(
				owner, entity -> true);
		check(HungryZombiePoseProviders.registeredOwners()
						.equals(Set.of(owner)),
				"Hungry Zombie pose owner registration drifted");
		expectFailure(() -> HungryZombiePoseProviders.register(
				owner, entity -> false));

		Set<ResourceLocation> owners =
				HungryZombiePoseProviders.registeredOwners();
		expectUnsupported(() -> owners.add(
				ResourceLocation.fromNamespaceAndPath(
						"rotp_test", "mutated")));
		HungryZombiePoseProviders.resetForTests();
		check(HungryZombiePoseProviders.registeredOwners()
						.isEmpty(),
				"Hungry Zombie pose test reset retained owners");
	}

	private static void expectFailure(Runnable action) {
		try {
			action.run();
			throw new AssertionError(
					"Expected duplicate pose provider failure");
		}
		catch (IllegalStateException expected) {}
	}

	private static void expectUnsupported(Runnable action) {
		try {
			action.run();
			throw new AssertionError(
					"Expected immutable owner snapshot");
		}
		catch (UnsupportedOperationException expected) {}
	}

	private static void check(
			boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
