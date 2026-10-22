package rotp.core.client.input;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;

import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.EntityActionInstance;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered;

/**
 * 1.16 InputHandler.handleMouseClickPowerHud (:931-935): while its player ran a continuous player action
 * (ContinuousActionInstance.getCurrentAction(mc.player).isPresent()), a vanilla attack or use click of the main hand whose
 * key had no action selected (no HUD power, hotbars off, no selected slot) was cancelled; the hand still swung, and a key
 * with an action kept its own went-off rule.
 */
public final class PlayerActionVanillaClickSmokeTest {
	private static final int ATTACK = 0;
	private static final int USE = 1;
	private static final int PICK_BLOCK = 2;

	private PlayerActionVanillaClickSmokeTest() {}

	public static void main(String[] args) {
		for (int button : new int[] { ATTACK, USE }) {
			String click = button == ATTACK ? "attack" : "use";

			InteractionKeyMappingTriggered free = new InteractionKeyMappingTriggered(button, null, InteractionHand.MAIN_HAND);
			check(InputHandler.cancelVanillaClickDuringPlayerAction(free, false, true) && free.isCanceled(),
					"a vanilla " + click + " click of a key with no ability must be cancelled during a player action");
			check(free.shouldSwingHand(), "1.16 still swung the hand of a vanilla " + click + " click it cancelled during"
					+ " a player action");

			InteractionKeyMappingTriggered bound = new InteractionKeyMappingTriggered(button, null, InteractionHand.MAIN_HAND);
			check(!InputHandler.cancelVanillaClickDuringPlayerAction(bound, true, true) && !bound.isCanceled()
					&& bound.shouldSwingHand(), "a key with an ability keeps its own rule during a player action: its"
							+ " refused " + click + " press lets the vanilla click through");

			InteractionKeyMappingTriggered idle = new InteractionKeyMappingTriggered(button, null, InteractionHand.MAIN_HAND);
			check(!InputHandler.cancelVanillaClickDuringPlayerAction(idle, false, false) && !idle.isCanceled()
					&& idle.shouldSwingHand(), "a vanilla " + click + " click must stay untouched with no player action");

			InteractionKeyMappingTriggered offHand = new InteractionKeyMappingTriggered(button, null, InteractionHand.OFF_HAND);
			check(!InputHandler.cancelVanillaClickDuringPlayerAction(offHand, false, true) && !offHand.isCanceled(),
					"1.16 left the off hand's " + click + " click to the main hand's decision");
		}
		InteractionKeyMappingTriggered pick = new InteractionKeyMappingTriggered(PICK_BLOCK, null, InteractionHand.MAIN_HAND);
		check(!InputHandler.cancelVanillaClickDuringPlayerAction(pick, false, true) && !pick.isCanceled(),
				"1.16 cancelled only the attack and use clicks during a player action, not pick block");

		try {
			Method running = Ability.class.getDeclaredMethod("getRunningPlayerAction", LivingEntity.class);
			int modifiers = running.getModifiers();
			check(Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers)
					&& running.getReturnType() == EntityActionInstance.class,
					"Ability.getRunningPlayerAction(LivingEntity) must be a public static lookup the client can ask");
		}
		catch (NoSuchMethodException error) {
			throw new AssertionError("Ability.getRunningPlayerAction(LivingEntity) is missing", error);
		}

		Path root = Path.of(System.getProperty("user.dir"));
		String inputHandler = read(root.resolve(
				"src/main/java/rotp/core/client/input/InputHandler.java")).replace("\r\n", "\n");
		int listener = inputHandler.indexOf(
				"\t@SubscribeEvent(priority = EventPriority.LOW)\n"
				+ "\tpublic void cancelVanillaClickInPlayerAction(InteractionKeyMappingTriggered event) {");
		int listenerEnd = inputHandler.indexOf("\n\t}\n", listener);
		String body = listener >= 0 && listenerEnd > listener ? inputHandler.substring(listener, listenerEnd) : "";
		int rule = body.indexOf("cancelVanillaClickDuringPlayerAction(event,");
		int key = body.indexOf("keysPressedWithAbility.contains(ClientKey.fromVanillaKeybind(event.getKeyMapping()))", rule);
		int action = body.indexOf("mc.player != null && Ability.getRunningPlayerAction(mc.player) != null", key);
		check(rule >= 0 && key > rule && action > key,
				"the player action click listener must feed the click key's last press and the local player's player"
						+ " action to the cancel rule");

		String schemeLookup = "ClientControlScheme controlScheme = getActiveControlSchemeForInput(key, keyModifier);\n\t\t\t\t";
		String abilityLookup = "CurInput input = getInputAbilitiesOnClick(controlScheme, key, keyModifier);\n\t\t\t\t";
		int input = inputHandler.indexOf("public boolean input(ClientKey key, int inputType, int modifiers)");
		int scheme = inputHandler.indexOf(schemeLookup, input);
		int noScheme = inputHandler.indexOf("if (controlScheme == null) {\n\t\t\t\t\tkeysPressedWithAbility.remove(key);\n"
				+ "\t\t\t\t\treturn false;\n\t\t\t\t}", scheme);
		int resolved = inputHandler.indexOf(abilityLookup, noScheme);
		int record = inputHandler.indexOf("if (input.bound) {\n\t\t\t\t\tkeysPressedWithAbility.add(key);\n\t\t\t\t}\n"
				+ "\t\t\t\telse {\n\t\t\t\t\tkeysPressedWithAbility.remove(key);\n\t\t\t\t}", resolved);
		int release = inputHandler.indexOf("case InputConstants.RELEASE -> {", record);
		int releaseEnd = inputHandler.indexOf("case InputConstants.REPEAT -> {", release);
		check(input >= 0 && scheme > input && noScheme == scheme + schemeLookup.length() && resolved > noScheme
				&& record == resolved + abilityLookup.length() && release > record && releaseEnd > release,
				"InputHandler.input must record, right as it looks a press up, whether its key had abilities bound (a press"
						+ " with no control scheme: none)");
		check(!inputHandler.substring(release, releaseEnd).contains("keysPressedWithAbility"),
				"the press record must outlive the release: the vanilla click of a short press comes in the next tick");

		int abilities = inputHandler.indexOf("private CurInput getInputAbilitiesOnClick(");
		int anyBound = inputHandler.indexOf(
				"if (!(heldBound.isEmpty() && clickBound.isEmpty())) {\n\t\t\t\tinput.bound = true;", abilities);
		check(abilities >= 0 && anyBound > abilities, "a key counts as having abilities when any is bound to it, active"
				+ " or not (1.16: an action selected in its hotbar)");

		int cleanup = inputHandler.indexOf("private void clearDisconnectedInputState()");
		int cleanupEnd = inputHandler.indexOf("\n\t}\n", cleanup);
		check(cleanup >= 0 && inputHandler.substring(cleanup, cleanupEnd).contains("keysPressedWithAbility.clear();"),
				"the press record must be cleared on logout");

		System.out.println("Player action vanilla click smoke test passed");
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
