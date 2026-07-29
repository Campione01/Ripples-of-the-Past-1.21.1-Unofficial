package com.github.standobyte.jojo.api.client.animation;

import com.github.standobyte.jojo.api.client.animation.AddonPlayerAnimations.PlayerAnimationState;

import net.minecraft.resources.ResourceLocation;

public final class AddonPlayerAnimationsSmokeTest {
	private AddonPlayerAnimationsSmokeTest() {}

	public static void run() {
		ResourceLocation providerId = id("rotp_test", "player_pose");
		AddonPlayerAnimations.register(providerId, 10,
				(player, partialTick) -> null);

		boolean duplicateRejected = false;
		try {
			AddonPlayerAnimations.register(providerId,
					(player, partialTick) -> null);
		}
		catch (IllegalArgumentException expected) {
			duplicateRejected = true;
		}
		check(duplicateRejected,
				"duplicate player animation provider id was accepted");

		PlayerAnimationState state = new PlayerAnimationState(
				id("rotp_test", "player"),
				"vine_attack",
				12.5F);
		check(state.animationSet().equals(id("rotp_test", "player")),
				"animation set id changed");
		check("vine_attack".equals(state.animation()),
				"animation name changed");
		check(state.timeInTicks() == 12.5F,
				"animation time changed");

		boolean invalidTimeRejected = false;
		try {
			new PlayerAnimationState(
					id("rotp_test", "player"),
					"vine_attack",
					Float.NaN);
		}
		catch (IllegalArgumentException expected) {
			invalidTimeRejected = true;
		}
		check(invalidTimeRejected,
				"non-finite player animation time was accepted");
	}

	private static ResourceLocation id(String namespace, String path) {
		return ResourceLocation.fromNamespaceAndPath(namespace, path);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
