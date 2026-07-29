package com.github.standobyte.jojo.api.client.render;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.ResourceLocation;

public final class HumanoidModelPostSetupSmokeTest {
	private HumanoidModelPostSetupSmokeTest() {}

	public static void run() {
		HumanoidModelPostSetup.resetForTests();
		List<String> calls = new ArrayList<>();
		ResourceLocation first = id("first");
		ResourceLocation second = id("second");
		HumanoidModelPostSetup.register(
				first, (entity, model) -> calls.add("first"));
		HumanoidModelPostSetup.register(
				second, (entity, model) -> calls.add("second"));
		HumanoidModelPostSetup.apply(null, null);
		check(calls.equals(List.of("first", "second")),
				"post-setup callback order changed");
		check(HumanoidModelPostSetup.registeredOwners().equals(
						List.of(first, second)),
				"post-setup owner order changed");
		expectIllegalState(() -> HumanoidModelPostSetup.register(
				first, (entity, model) -> {}));

		String mixin = read(Path.of(System.getProperty("user.dir"))
				.resolve("src/main/java/com/github/standobyte/jojo/"
						+ "mixin/client/v1_21_1_modelanim/player/"
						+ "HumanoidModelMixin.java"));
		int carryPose = mixin.indexOf(
				"jojo_ripples$cocoJumboCarryPose");
		int callback = mixin.indexOf(
				"HumanoidModelPostSetup.apply(", carryPose);
		int nextMethod = mixin.indexOf("\n\t@Override", callback);
		check(carryPose >= 0
						&& callback > carryPose
						&& nextMethod > callback,
				"post-setup callback is not the final carry-pose step");
		HumanoidModelPostSetup.resetForTests();
	}

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(
				"rotp_test", path);
	}

	private static String read(Path path) {
		try {
			return Files.readString(path);
		}
		catch (IOException error) {
			throw new AssertionError("failed to read " + path, error);
		}
	}

	private static void expectIllegalState(Runnable action) {
		try {
			action.run();
		}
		catch (IllegalStateException expected) {
			return;
		}
		throw new AssertionError(
				"duplicate post-setup owner was accepted");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
