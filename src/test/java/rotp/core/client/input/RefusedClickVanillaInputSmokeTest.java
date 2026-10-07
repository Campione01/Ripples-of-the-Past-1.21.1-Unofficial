package rotp.core.client.input;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 1.16 InputHandler.handleMouseClickPowerHud: the vanilla click is cancelled only when the action went off, so a
 * refused click ability (no energy, no target, a missing skill) leaves the player's own left click alone.
 */
public final class RefusedClickVanillaInputSmokeTest {
	private RefusedClickVanillaInputSmokeTest() {}

	public static void main(String[] args) {
		// attackKey, standScheme, holdAbilityResolved, clickAbilityResolved, clickAbilityUsable
		check(!InputHandler.shouldCancelVanillaForAbilityPress(true, false, false, true, false),
				"a refused click ability on the attack key of a non-Stand HUD must let the vanilla left click through");
		check(InputHandler.shouldCancelVanillaForAbilityPress(true, false, false, true, true),
				"a click ability that goes off on the attack key must cancel the vanilla left click");
		check(InputHandler.shouldCancelVanillaForAbilityPress(true, false, true, true, false),
				"a hold ability on the attack key must keep the key even when the click ability is refused");
		check(InputHandler.shouldCancelVanillaForAbilityPress(true, false, true, false, false),
				"a hold ability alone on the attack key must cancel the vanilla left click");
		check(!InputHandler.shouldCancelVanillaForAbilityPress(true, false, false, false, false),
				"an attack key without any ability must stay vanilla");
		check(!InputHandler.shouldCancelVanillaForAbilityPress(false, false, false, false, false)
				&& !InputHandler.shouldCancelVanillaForAbilityPress(true, true, false, false, false)
				&& !InputHandler.shouldCancelVanillaForAbilityPress(false, true, false, false, false),
				"a key without any ability must stay vanilla on every key and scheme");
		check(InputHandler.shouldCancelVanillaForAbilityPress(false, false, false, true, false),
				"a refused click ability on a key other than the attack key must keep cancelling the key (out of scope)");
		check(InputHandler.shouldCancelVanillaForAbilityPress(true, true, false, true, false),
				"a refused click ability in a Stand scheme must keep cancelling the attack key (out of scope)");
		check(InputHandler.shouldCancelVanillaForAbilityPress(false, true, false, true, false),
				"a refused click ability in a Stand scheme must keep cancelling other keys (out of scope)");

		Path root = Path.of(System.getProperty("user.dir"));
		String inputHandler = read(root.resolve(
				"src/main/java/rotp/core/client/input/InputHandler.java"));
		int inputMethod = inputHandler.indexOf("public boolean input(ClientKey key, int inputType, int modifiers)");
		int resolved = inputHandler.indexOf("getInputAbilitiesOnClick(controlScheme, key, keyModifier)", inputMethod);
		int decision = inputHandler.indexOf("cancelVanilla |= shouldCancelVanillaForAbilityPress(", resolved);
		int attackKey = inputHandler.indexOf(
				"key.equals(ClientKey.fromVanillaKeybind(mc.options.keyAttack))", decision);
		int standScheme = inputHandler.indexOf("controlScheme.powerClassCosmetic == PowerClass.STAND", attackKey);
		int usable = inputHandler.indexOf(
				"clickAbility != null && clickAbility.curActiveAbility.conditionCheck.isPositive()", standScheme);
		int timer = inputHandler.indexOf("new HeldKeyTimer(key, cancelVanilla, keyModifier)", usable);
		check(inputMethod >= 0 && resolved > inputMethod && decision > resolved && attackKey > decision
				&& standScheme > attackKey && usable > standScheme && timer > usable,
				"InputHandler.input must take the vanilla cancel of an ability press from the refused-click rule"
				+ " before it stores the held key");
		check(!inputHandler.contains("cancelVanilla |= heldAbility != null || clickAbility != null"),
				"InputHandler.input still cancels the vanilla click for every bound ability");

		System.out.println("Refused click vanilla input smoke test passed");
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
