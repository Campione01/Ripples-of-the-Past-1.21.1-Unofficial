package com.github.standobyte.jojo.api.trade;

import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public final class ContextualVillagerTradesSmokeTest {
	private ContextualVillagerTradesSmokeTest() {}

	public static void run() {
		ContextualVillagerTrades.clearForTests();
		ResourceLocation owner = id("owner");
		ResourceLocation group = id("expert_maps");
		ContextualVillagerTrades.register(
				owner,
				group,
				context -> null);
		check(ContextualVillagerTrades.registeredOwners()
				.equals(java.util.List.of(owner)),
				"contextual trade owner registration order changed");
		try {
			ContextualVillagerTrades.register(
					owner,
					id("other_group"),
					context -> null);
			throw new AssertionError(
					"duplicate contextual trade owner accepted");
		}
		catch (IllegalStateException expected) {}

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
