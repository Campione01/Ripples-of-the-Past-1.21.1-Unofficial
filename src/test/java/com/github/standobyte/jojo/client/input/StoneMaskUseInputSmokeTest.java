package com.github.standobyte.jojo.client.input;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class StoneMaskUseInputSmokeTest {
	private StoneMaskUseInputSmokeTest() {}

	public static void main(String[] args) {
		check(InputHandler.shouldPreserveStoneMaskKnifeUse(
				true, true, 1, 0),
				"one main-hand knife must preserve worn Stone Mask use");
		check(InputHandler.shouldPreserveStoneMaskKnifeUse(
				true, true, 0, 1),
				"one off-hand knife must preserve worn Stone Mask use");
		check(!InputHandler.shouldPreserveStoneMaskKnifeUse(
				false, true, 1, 0),
				"non-Use input entered the Stone Mask route");
		check(!InputHandler.shouldPreserveStoneMaskKnifeUse(
				true, false, 1, 0),
				"a knife without a worn Stone Mask bypassed Stand input");
		check(!InputHandler.shouldPreserveStoneMaskKnifeUse(
				true, true, 2, 0),
				"stacked knives bypassed the one-knife requirement");
		check(!InputHandler.shouldPreserveStoneMaskKnifeUse(
				true, true, 1, 1),
				"two hands each holding a knife bypassed the one-knife requirement");
		check(!InputHandler.shouldPreserveStoneMaskKnifeUse(
				true, true, 0, 0),
				"an empty hand bypassed the one-knife requirement");

		Path root = Path.of(System.getProperty("user.dir"));
		String inputHandler = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/client/input/InputHandler.java"));
		int selector = inputHandler.indexOf(
				"private ClientControlScheme getActiveControlSchemeForInput");
		int stoneMaskGuard = inputHandler.indexOf(
				"shouldPreserveStoneMaskKnifeUse(vanillaUseTrigger)", selector);
		int standGuard = inputHandler.indexOf(
				"shouldPreserveUnsummonedStandVanillaUsePress(",
				stoneMaskGuard);
		int directPower = inputHandler.indexOf(
				"getDirectPlayerPowerControlScheme(key, keyModifier)",
				standGuard);
		check(selector >= 0 && stoneMaskGuard > selector
				&& standGuard > stoneMaskGuard
				&& directPower > standGuard,
				"Stone Mask use guard does not precede ability arbitration");
		check(inputHandler.contains("instanceof StoneMaskItem")
				&& inputHandler.contains("instanceof KnifeItem")
				&& inputHandler.contains("stack.getCount()"),
				"live item state is not wired into the Stone Mask use guard");

		String clickInput = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/client/input/StandVanillaClickInput.java"));
		int semanticGuard = clickInput.indexOf(
				"shouldPreserveSemanticVanillaUsePress()");
		int standItemUse = clickInput.indexOf(
				"handleStandItemUseInput(event, keyCode, inputHandler)",
				semanticGuard);
		int mappedAbility = clickInput.indexOf(
				"handleVanillaMappedAbilityInput(event, keyCode)",
				standItemUse);
		check(semanticGuard >= 0 && standItemUse > semanticGuard
				&& mappedAbility > standItemUse,
				"semantic vanilla use does not precede Stand RMB ownership");

		System.out.println("Stone Mask use input smoke test passed");
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
