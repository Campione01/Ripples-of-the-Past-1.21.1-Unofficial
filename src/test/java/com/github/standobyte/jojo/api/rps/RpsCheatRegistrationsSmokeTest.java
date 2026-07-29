package com.github.standobyte.jojo.api.rps;

import java.util.List;

import net.minecraft.resources.ResourceLocation;

public final class RpsCheatRegistrationsSmokeTest {
	private RpsCheatRegistrationsSmokeTest() {}

	public static void run() {
		check(RpsCheatKind.values().length == 1
						&& RpsCheatKind.values()[0]
								== RpsCheatKind.MIND_READ,
				"RPS cheat kind set is not closed to MIND_READ");

		RpsCheatRegistrations.resetForTests();
		ResourceLocation owner = id("mind_read");
		RpsCheatRegistrations.registerStand(
				owner,
				() -> null,
				RpsCheatSpec.mindRead(() -> null));
		check(RpsCheatRegistrations.registeredOwners()
						.equals(List.of(owner)),
				"RPS registration owner was not retained");
		expectIllegalState(() ->
				RpsCheatRegistrations.registerStand(
						owner,
						() -> null,
						RpsCheatSpec.mindRead(() -> null)));
		RpsCheatRegistrations.resetForTests();
	}

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(
				"rotp_test", path);
	}

	private static void expectIllegalState(Runnable action) {
		try {
			action.run();
		}
		catch (IllegalStateException expected) {
			return;
		}
		throw new AssertionError(
				"duplicate RPS registration owner was accepted");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
