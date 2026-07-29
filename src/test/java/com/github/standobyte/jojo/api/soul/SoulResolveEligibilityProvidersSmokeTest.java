package com.github.standobyte.jojo.api.soul;

import java.util.List;

import net.minecraft.resources.ResourceLocation;

public final class SoulResolveEligibilityProvidersSmokeTest {
	private SoulResolveEligibilityProvidersSmokeTest() {}

	public static void run() {
		SoulResolveEligibilityProviders.resetForTests();
		SoulResolveQuery deniedByDefault = new SoulResolveQuery(
				null, null, null, null, false, false);
		SoulResolveQuery allowedByDefault = new SoulResolveQuery(
				null, null, null, null, false, true);

		check(!SoulResolveEligibilityProviders.isEligible(deniedByDefault),
				"core soul Resolve denial changed without providers");
		check(SoulResolveEligibilityProviders.isEligible(allowedByDefault),
				"core soul Resolve allowance changed without providers");

		ResourceLocation allow = id("allow");
		ResourceLocation failing = id("failing");
		ResourceLocation deny = id("deny");
		SoulResolveEligibilityProviders.register(
				allow, query -> SoulResolveDecision.ALLOW);
		SoulResolveEligibilityProviders.register(
				failing, query -> {
					throw new IllegalStateException("expected test failure");
				});
		check(SoulResolveEligibilityProviders.isEligible(deniedByDefault),
				"addon soul Resolve allowance was ignored");
		SoulResolveEligibilityProviders.register(
				deny, query -> SoulResolveDecision.DENY);
		check(!SoulResolveEligibilityProviders.isEligible(allowedByDefault),
				"soul Resolve denial did not take precedence");
		check(SoulResolveEligibilityProviders.registeredOwners().equals(
				List.of(allow, failing, deny)),
				"soul Resolve provider order changed");
		expectIllegalState(() -> SoulResolveEligibilityProviders.register(
				allow, query -> SoulResolveDecision.PASS));
		SoulResolveEligibilityProviders.resetForTests();
	}

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath("rotp_test", path);
	}

	private static void expectIllegalState(Runnable action) {
		try {
			action.run();
		}
		catch (IllegalStateException expected) {
			return;
		}
		throw new AssertionError(
				"duplicate soul Resolve provider was accepted");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
