package com.github.standobyte.jojo.api.client.render;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.resources.ResourceLocation;

public final class FirstPersonStandRenderPoliciesSmokeTest {
	private FirstPersonStandRenderPoliciesSmokeTest() {}

	public static void run() {
		FirstPersonStandRenderPolicies.resetForTests();
		ResourceLocation failed = id("failed");
		ResourceLocation active = id("active");
		AtomicInteger calls = new AtomicInteger();

		FirstPersonStandRenderPolicies.register(failed, query -> {
			calls.incrementAndGet();
			throw new IllegalStateException("expected smoke failure");
		});
		FirstPersonStandRenderPolicies.register(active, query -> {
			calls.incrementAndGet();
			return true;
		});

		check(FirstPersonStandRenderPolicies.shouldSuppress(
						new FirstPersonStandRenderQuery(
								null, null, 0.5F)),
				"first-person Stand policy failure prevented veto");
		check(calls.get() == 2,
				"first-person Stand policies did not run in order");
		check(FirstPersonStandRenderPolicies.registeredOwners()
						.equals(List.of(failed, active)),
				"first-person Stand policy order changed");
		expectIllegalState(() ->
				FirstPersonStandRenderPolicies.register(
						active, query -> false));
		FirstPersonStandRenderPolicies.resetForTests();
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
				"duplicate first-person Stand policy was accepted");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
