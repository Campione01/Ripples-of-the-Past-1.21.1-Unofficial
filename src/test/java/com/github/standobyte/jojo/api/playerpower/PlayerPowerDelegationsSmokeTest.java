package com.github.standobyte.jojo.api.playerpower;

import net.minecraft.resources.ResourceLocation;

public final class PlayerPowerDelegationsSmokeTest {
	private static final PlayerPowerDelegationProvider ALLOW =
			(power, retained) -> true;
	private static final PlayerPowerDelegationProvider DENY =
			(power, retained) -> false;

	private PlayerPowerDelegationsSmokeTest() {}

	public static void run() {
		PlayerPowerDelegations.clearForTests();
		ResourceLocation owner = id("owner");
		ResourceLocation temporary = id("temporary");
		register(owner, temporary, ALLOW);
		register(owner, temporary, ALLOW);
		check(PlayerPowerDelegations.registeredOwners()
				.equals(java.util.List.of(owner)),
				"delegation owner registration order changed");

		expectDuplicate(() -> PlayerPowerDelegations.register(
				owner,
				id("other_temporary"),
				ALLOW));
		expectDuplicate(() -> PlayerPowerDelegations.register(
				id("other_owner"),
				temporary,
				ALLOW));
		expectDuplicate(() ->
				register(owner, temporary, DENY));

		ResourceLocation capturedOwner = id("captured_owner");
		ResourceLocation capturedTemporary = id("captured_temporary");
		PlayerPowerDelegationProvider captured =
				capturedProvider(true);
		register(capturedOwner, capturedTemporary, captured);
		register(capturedOwner, capturedTemporary, captured);
		expectDuplicate(() -> register(
				capturedOwner,
				capturedTemporary,
				capturedProvider(true)));
		PlayerPowerDelegations.clearForTests();
	}

	private static void register(
			ResourceLocation owner,
			ResourceLocation temporary,
			PlayerPowerDelegationProvider provider) {
		PlayerPowerDelegations.register(
				owner,
				temporary,
				provider);
	}

	private static PlayerPowerDelegationProvider capturedProvider(
			boolean decision) {
		return (power, retained) -> decision;
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
