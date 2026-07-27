package com.github.standobyte.jojo.api.client.render;

import java.util.Set;

import net.minecraft.resources.ResourceLocation;

public final class AddonPostEffectRegistrationSmokeTest {
	private AddonPostEffectRegistrationSmokeTest() {}

	public static void run() {
		ResourceLocation first = id("rotp_test", "time_erase");
		ResourceLocation second =
				id("rotp_test", "shaders/post/time_erase_iris.json");
		ResourceLocation canonicalFirst =
				id("rotp_test", "shaders/post/time_erase.json");

		check(canonicalFirst.equals(
				AddonPostEffect.canonicalPostChain(first)),
				"shorthand post-chain route was not canonicalized");
		check(second.equals(
				AddonPostEffect.canonicalPostChain(second)),
				"canonical post-chain route was not idempotent");
		check(Set.of(canonicalFirst, second).size() == 2,
				"canonical route smoke fixture is invalid");
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
