package com.github.standobyte.jojo.api.client.render;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.ResourceLocation;

public final class FirstPersonPostArmLayersSmokeTest {
	private FirstPersonPostArmLayersSmokeTest() {}

	public static void run() {
		List<String> calls = new ArrayList<>();
		List<String> failures = new ArrayList<>();
		FirstPersonPostArmLayers.Registration late =
				FirstPersonPostArmLayers.register(
						id("rotp_test", "late"),
						10,
						context -> calls.add("late"));
		FirstPersonPostArmLayers.Registration tiedSecond =
				FirstPersonPostArmLayers.register(
						id("rotp_test", "tied_second"),
						0,
						context -> calls.add("tied_second"));
		FirstPersonPostArmLayers.Registration throwing =
				FirstPersonPostArmLayers.register(
						id("rotp_test", "middle_throwing"),
						5,
						context -> {
							calls.add("middle_throwing");
							throw new IllegalStateException("expected");
						});
		FirstPersonPostArmLayers.Registration tiedFirst =
				FirstPersonPostArmLayers.register(
						id("rotp_test", "tied_first"),
						0,
						context -> calls.add("tied_first"));
		try {
			FirstPersonPostArmLayers.dispatchForTest(
					null,
					(id, throwable) -> failures.add(
							id + ":" + throwable.getMessage()));
			check(calls.equals(List.of(
							"tied_first",
							"tied_second",
							"middle_throwing",
							"late")),
					"post-arm layers must use order then ID");
			check(failures.equals(List.of(
							"rotp_test:middle_throwing:expected")),
					"a failed contributor must be reported once");

			boolean duplicateRejected = false;
			try {
				FirstPersonPostArmLayers.register(
						id("rotp_test", "late"),
						0,
						context -> {});
			}
			catch (IllegalStateException expected) {
				duplicateRejected = true;
			}
			check(duplicateRejected,
					"duplicate post-arm layer IDs must be rejected");
		}
		finally {
			tiedFirst.close();
			throwing.close();
			tiedSecond.close();
			late.close();
		}
		calls.clear();
		FirstPersonPostArmLayers.dispatchForTest(
				null,
				(id, throwable) -> failures.add(id.toString()));
		check(calls.isEmpty(),
				"closed post-arm layer registrations must be removed");
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
