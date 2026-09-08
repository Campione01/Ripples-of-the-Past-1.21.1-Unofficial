package com.github.standobyte.jojo.client.input;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class StoneMaskUseInputSmokeTest {
	private StoneMaskUseInputSmokeTest() {}

	public static void main(String[] args) {
		verifyStandDiscUseInput();
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
		int useMapping = inputHandler.indexOf(
				"key.equals(ClientKey.fromVanillaKeybind(mc.options.keyUse))",
				selector);
		int discGuard = inputHandler.indexOf(
				"shouldPreserveStandDiscUse(vanillaUseTrigger)", selector);
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
		check(useMapping > selector && discGuard > useMapping
				&& stoneMaskGuard > discGuard,
				"Stand Disc use must follow the configured Use mapping before ability arbitration");
		check(inputHandler.contains(
				"player.getMainHandItem().getItem() instanceof StandDiscItem")
				&& inputHandler.contains(
						"player.getOffhandItem().getItem() instanceof StandDiscItem"),
				"both player hands must feed the Stand Disc use guard");
		int semanticSelector = inputHandler.indexOf(
				"boolean shouldPreserveSemanticVanillaUsePress()");
		int semanticDiscGuard = inputHandler.indexOf(
				"shouldPreserveStandDiscUse(true)", semanticSelector);
		int semanticMaskGuard = inputHandler.indexOf(
				"shouldPreserveStoneMaskKnifeUse(true)", semanticSelector);
		int semanticStandGuard = inputHandler.indexOf(
				"shouldPreserveUnsummonedStandVanillaUsePress(",
				semanticSelector);
		check(semanticSelector >= 0 && semanticDiscGuard > semanticSelector
				&& semanticMaskGuard > semanticDiscGuard
				&& semanticStandGuard > semanticMaskGuard,
				"semantic Stand Disc use must preserve the existing mask and unsummoned guards");
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
		String vanillaKeybinds = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/client/input/VanillaKeybinds.java"));
		check(vanillaKeybinds.contains(".shouldPreserveSemanticVanillaUsePress()"),
				"Shift combat conflict context must honor Stand Disc semantic Use");
		check(!VanillaKeybinds.shouldActivateGrabChargedHeavyConflictContext(
				true, InputHandler.shouldPreserveStandDiscUse(true, true, false))
				&& !VanillaKeybinds.shouldActivateGrabChargedHeavyConflictContext(
						true, InputHandler.shouldPreserveStandDiscUse(true, false, true)),
				"either-hand Stand Disc must preserve Shift+Use from the combat mapping");

		System.out.println("Stone Mask and Stand Disc use input smoke test passed");
	}

	private static void verifyStandDiscUseInput() {
		for (int hands = 0; hands < 4; hands++) {
			boolean mainHandDisc = (hands & 1) != 0;
			boolean offHandDisc = (hands & 2) != 0;
			check(InputHandler.shouldPreserveStandDiscUse(
					true, mainHandDisc, offHandDisc) == (hands != 0),
					"only an either-hand Stand Disc may preserve Use");
			check(!InputHandler.shouldPreserveStandDiscUse(
					false, mainHandDisc, offHandDisc),
					"a Stand Disc must not suppress a non-Use custom ability key");
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
