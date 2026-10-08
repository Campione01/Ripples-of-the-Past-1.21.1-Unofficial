package rotp.core.client.input;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;

/**
 * 1.16 InputHandler.handleMouseClickPowerHud and handleCustomKeybind: the vanilla input is cancelled only when the
 * action went off, on every key and for every power, so a refused ability (no energy, no target, a cooldown, a
 * missing skill) leaves the player's own click alone. A hold action that does not start is refused the same way.
 */
public final class RefusedClickVanillaInputSmokeTest {
	private RefusedClickVanillaInputSmokeTest() {}

	public static void main(String[] args) {
		ConditionCheck usable = ConditionCheck.POSITIVE;
		ConditionCheck refused = ConditionCheck.NEGATIVE;
		ConditionCheck keepsHolding = ConditionCheck.NEGATIVE_CONTINUE_HOLD;

		// hold ability on the key, click ability on the key (null: none bound)
		check(!InputHandler.shouldCancelVanillaForAbilityPress(null, refused),
				"a refused click ability must let the vanilla input of its key through");
		check(InputHandler.shouldCancelVanillaForAbilityPress(null, usable),
				"a click ability that goes off must cancel the vanilla input of its key");
		check(!InputHandler.shouldCancelVanillaForAbilityPress(refused, null),
				"a hold ability whose start is refused must let the vanilla input of its key through");
		check(InputHandler.shouldCancelVanillaForAbilityPress(usable, null),
				"a hold ability that starts must cancel the vanilla input of its key");
		check(InputHandler.shouldCancelVanillaForAbilityPress(keepsHolding, null),
				"a hold ability that starts without a valid target yet must cancel the vanilla input of its key");
		check(!InputHandler.shouldCancelVanillaForAbilityPress(null, keepsHolding),
				"a click ability has no hold to continue: a refused one must let the vanilla input through");
		check(!InputHandler.shouldCancelVanillaForAbilityPress(refused, refused),
				"a key whose hold and click abilities are both refused must stay vanilla");
		check(InputHandler.shouldCancelVanillaForAbilityPress(usable, refused),
				"a hold ability that starts must keep the key even when the click ability is refused");
		check(InputHandler.shouldCancelVanillaForAbilityPress(refused, usable),
				"a click ability that can go off must keep the key even when the hold ability is refused");
		check(!InputHandler.shouldCancelVanillaForAbilityPress(null, null),
				"a key without any ability must stay vanilla");

		check(InputHandler.abilityPressGoesOff(usable, InputMethod.CLICK)
				&& InputHandler.abilityPressGoesOff(usable, InputMethod.HOLD)
				&& InputHandler.abilityPressGoesOff(keepsHolding, InputMethod.HOLD)
				&& !InputHandler.abilityPressGoesOff(keepsHolding, InputMethod.CLICK)
				&& !InputHandler.abilityPressGoesOff(refused, InputMethod.CLICK)
				&& !InputHandler.abilityPressGoesOff(refused, InputMethod.HOLD)
				&& !InputHandler.abilityPressGoesOff(null, InputMethod.CLICK)
				&& !InputHandler.abilityPressGoesOff(null, InputMethod.HOLD),
				"an ability press goes off when its check is positive, or for a hold when the hold may continue");

		Path root = Path.of(System.getProperty("user.dir"));
		String inputHandler = read(root.resolve(
				"src/main/java/rotp/core/client/input/InputHandler.java"));
		int inputMethod = inputHandler.indexOf("public boolean input(ClientKey key, int inputType, int modifiers)");
		int resolved = inputHandler.indexOf("getInputAbilitiesOnClick(controlScheme, key, keyModifier)", inputMethod);
		int decision = inputHandler.indexOf("cancelVanilla |= shouldCancelVanillaForAbilityPress(", resolved);
		int hold = inputHandler.indexOf(
				"heldAbility != null ? heldAbility.curActiveAbility.conditionCheck : null,", decision);
		int click = inputHandler.indexOf(
				"clickAbility != null ? clickAbility.curActiveAbility.conditionCheck : null);", hold);
		int timer = inputHandler.indexOf("new HeldKeyTimer(key, cancelVanilla, keyModifier)", click);
		check(inputMethod >= 0 && resolved > inputMethod && decision > resolved && hold > decision
				&& click > hold && timer > click,
				"InputHandler.input must take the vanilla cancel of an ability press from the client checks of the"
				+ " hold and click abilities before it stores the held key");
		String decisionCall = inputHandler.substring(decision, timer);
		check(!decisionCall.contains("keyAttack") && !decisionCall.contains("keyUse")
				&& !decisionCall.contains("PowerClass.STAND"),
				"the refused-press rule must not depend on the key or on the power class of the control scheme");
		check(!inputHandler.contains("cancelVanilla |= heldAbility != null || clickAbility != null"),
				"InputHandler.input still cancels the vanilla click for every bound ability");

		int send = inputHandler.indexOf("private void doClickInput(InputEventType type, ClientKey key,");
		int sendEnd = inputHandler.indexOf("private void doReleaseInput(short keyId)", send);
		check(send >= 0 && sendEnd > send
				&& inputHandler.substring(send, sendEnd).contains("if (abilityPressGoesOff(conditionCheck, inputMethod)) {"),
				"the local start of an ability press and the vanilla cancel must use the same went-off rule");

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
