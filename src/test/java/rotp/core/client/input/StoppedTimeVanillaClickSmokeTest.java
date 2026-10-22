package rotp.core.client.input;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered;

/**
 * 1.16 InputHandler.shouldVanillaInputStun (:1031-1033): ModStatusEffects.isStunned(mc.player) || !mc.player.canUpdate(),
 * the player stopped in someone else's time; a vanilla attack or use click did nothing then. The port's listener
 * (cancelVanillaClickInStun) asks both: the stun and the client's stopped-time state (Ability.isUserStoppedInTime's).
 */
public final class StoppedTimeVanillaClickSmokeTest {
	private static final int ATTACK = 0;
	private static final int USE = 1;
	private static final int PICK_BLOCK = 2;

	private StoppedTimeVanillaClickSmokeTest() {}

	public static void main(String[] args) {
		for (int button : new int[] { ATTACK, USE }) {
			String click = button == ATTACK ? "attack" : "use";
			InteractionKeyMappingTriggered stopped = new InteractionKeyMappingTriggered(button, null, InteractionHand.MAIN_HAND);
			check(InputHandler.cancelVanillaClickWhileStunned(stopped, false, true) && stopped.isCanceled()
					&& !stopped.shouldSwingHand(), "1.16 cancelled a vanilla " + click + " click while its player was stopped in"
							+ " someone else's time (!canUpdate)");
			InteractionKeyMappingTriggered stunned = new InteractionKeyMappingTriggered(button, null, InteractionHand.MAIN_HAND);
			check(InputHandler.cancelVanillaClickWhileStunned(stunned, true, false) && stunned.isCanceled()
					&& !stunned.shouldSwingHand(), "a vanilla " + click + " click must still be cancelled while stunned");
			InteractionKeyMappingTriggered free = new InteractionKeyMappingTriggered(button, null, InteractionHand.MAIN_HAND);
			check(!InputHandler.cancelVanillaClickWhileStunned(free, false, false) && !free.isCanceled() && free.shouldSwingHand(),
					"a vanilla " + click + " click must stay untouched while neither stunned nor stopped in time");
		}
		InteractionKeyMappingTriggered pick = new InteractionKeyMappingTriggered(PICK_BLOCK, null, InteractionHand.MAIN_HAND);
		check(!InputHandler.cancelVanillaClickWhileStunned(pick, true, true) && !pick.isCanceled(),
				"only the attack and use clicks are cancelled, not pick block");

		String inputHandler = read(Path.of(System.getProperty("user.dir"))
				.resolve("src/main/java/rotp/core/client/input/InputHandler.java")).replace("\r\n", "\n");
		int listener = inputHandler.indexOf("\tpublic void cancelVanillaClickInStun(InteractionKeyMappingTriggered event) {");
		int listenerEnd = inputHandler.indexOf("\n\t}\n", listener);
		String body = listener >= 0 && listenerEnd > listener ? inputHandler.substring(listener, listenerEnd) : "";
		int rule = body.indexOf("cancelVanillaClickWhileStunned(event,");
		int stun = body.indexOf("ModStatusEffects.isStunned(mc.player)", rule);
		int time = body.indexOf("ClientTimeStopHandler.isTimeStoppedStatic() && !TimeStopClientAwareness.canMove()", stun);
		check(rule >= 0 && stun > rule && time > stun,
				"the stun click listener must feed the local player's stun and its stopped-time state to the cancel rule");

		System.out.println("Stopped time vanilla click smoke test passed");
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
