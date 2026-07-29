package com.github.standobyte.jojo.api.playerpower;

import net.minecraft.resources.ResourceLocation;

public final class PlayerPowerDelegationsSmokeTest {
	private PlayerPowerDelegationsSmokeTest() {}

	public static void run() {
		PlayerPowerDelegations.clearForTests();
		ResourceLocation owner = id("owner");
		ResourceLocation temporary = id("temporary");
		PlayerPowerDelegations.register(
				owner, temporary, (power, retained) -> true);
		check(PlayerPowerDelegations.registeredOwners()
				.equals(java.util.List.of(owner)),
				"delegation owner registration order changed");

		expectDuplicate(() -> PlayerPowerDelegations.register(
				owner,
				id("other_temporary"),
				(power, retained) -> true));
		expectDuplicate(() -> PlayerPowerDelegations.register(
				id("other_owner"),
				temporary,
				(power, retained) -> true));
		PlayerPowerDelegations.clearForTests();
	}

	private static void expectDuplicate(Runnable registration) {
		try {
			registration.run();
			throw new AssertionError(
					"duplicate delegation registration accepted");
		}
		catch (IllegalStateException expected) {}
	}

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(
				"rotp_test", path);
	}

	private static void check(
			boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
