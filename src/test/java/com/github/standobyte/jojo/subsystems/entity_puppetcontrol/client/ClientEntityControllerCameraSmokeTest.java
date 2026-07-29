package com.github.standobyte.jojo.subsystems.entity_puppetcontrol.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ClientEntityControllerCameraSmokeTest {
	private ClientEntityControllerCameraSmokeTest() {}

	public static void run() {
		Path root = Path.of(System.getProperty("user.dir"));
		String controller = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/subsystems/"
				+ "entity_puppetcontrol/client/"
				+ "ClientEntityController.java"));
		check(controller.contains(
						"public boolean controlsLocalPlayerCamera()")
						&& controller.contains("return false;"),
				"controller camera extension point is missing");

		String mixin = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/mixin/"
				+ "entity_like_player/puppetcontrol/client/"
				+ "LocalPlayerControllerCameraMixin.java"));
		for (String token : new String[] {
			"method = \"isControlledCamera\"",
			"at = @At(\"RETURN\")",
			"minecraft.player == (Object) this",
			"controller.entity == minecraft.getCameraEntity()",
			"controller.controlsLocalPlayerCamera()",
			"ci.setReturnValue(true)"
		}) {
			check(mixin.contains(token),
					"controller camera hook missing: " + token);
		}
	}

	private static String read(Path path) {
		try {
			return Files.readString(path);
		}
		catch (IOException error) {
			throw new AssertionError("failed to read " + path, error);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
