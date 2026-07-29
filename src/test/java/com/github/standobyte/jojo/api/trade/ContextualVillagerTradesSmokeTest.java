package com.github.standobyte.jojo.api.trade;

import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public final class ContextualVillagerTradesSmokeTest {
	private static final ContextualVillagerTradeProvider PRIMARY_PROVIDER =
			context -> null;
	private static final ContextualVillagerTradeProvider OTHER_PROVIDER =
			context -> null;

	private ContextualVillagerTradesSmokeTest() {}

	public static void run() {
		ContextualVillagerTrades.clearForTests();
		ResourceLocation owner = id("owner");
		ResourceLocation group = id("expert_maps");
		register(owner, group, PRIMARY_PROVIDER);
		register(owner, group, PRIMARY_PROVIDER);
		check(ContextualVillagerTrades.registeredOwners()
				.equals(java.util.List.of(owner)),
				"contextual trade owner registration order changed");
		expectConflict(() ->
				register(owner, id("other_group"), PRIMARY_PROVIDER));
		expectConflict(() ->
				register(owner, group, OTHER_PROVIDER));

		ResourceLocation capturedOwner = id("captured_owner");
		ContextualVillagerTradeProvider captured =
				capturedProvider("payload");
		register(capturedOwner, group, captured);
		register(capturedOwner, group, captured);
		expectConflict(() -> register(
				capturedOwner,
				group,
				capturedProvider("payload")));

		CompoundTag root = new CompoundTag();
		UUID firstPlayer = UUID.randomUUID();
		UUID secondPlayer = UUID.randomUUID();
		check(ContextualVillagerTrades.markAttemptIfEligible(
				root, group, firstPlayer),
				"first player attempt was rejected");
		check(!ContextualVillagerTrades.markAttemptIfEligible(
				root, group, firstPlayer),
				"duplicate player attempt was accepted");
		check(ContextualVillagerTrades.markAttemptIfEligible(
				root, group, secondPlayer),
				"second player attempt was incorrectly shared");

		CompoundTag reloaded = root.copy();
		check(!ContextualVillagerTrades.markAttemptIfEligible(
				reloaded, group, firstPlayer),
				"player attempt was lost after NBT reload");
		ContextualVillagerTrades.markSuccess(
				reloaded, group);
		check(ContextualVillagerTrades.hasSucceeded(
				reloaded, group),
				"successful trade state was not retained");
		check(!ContextualVillagerTrades.markAttemptIfEligible(
				reloaded, group, UUID.randomUUID()),
				"new attempt was accepted after group success");
		ContextualVillagerTrades.clearForTests();
	}

	private static void register(
			ResourceLocation owner,
			ResourceLocation group,
			ContextualVillagerTradeProvider provider) {
		ContextualVillagerTrades.register(
				owner,
				group,
				provider);
	}

	private static ContextualVillagerTradeProvider capturedProvider(
			String payload) {
		return context -> {
			if (payload.isEmpty()) {
				throw new IllegalStateException(
						"invalid smoke payload");
			}
			return null;
		};
	}

	private static void expectConflict(Runnable registration) {
		try {
			registration.run();
			throw new AssertionError(
					"conflicting contextual trade registration accepted");
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
