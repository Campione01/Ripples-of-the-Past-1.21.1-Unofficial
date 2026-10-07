package rotp.core.client.input;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered;

/**
 * 1.16 InputHandler.cancelClickInput: while the Hamon user meditates every vanilla click (attack, use, pick block)
 * is cancelled together with its hand swing, before any other click handler runs.
 */
public final class MeditationVanillaClickSmokeTest {
	private static final int ATTACK = 0;
	private static final int USE = 1;
	private static final int PICK_BLOCK = 2;

	private MeditationVanillaClickSmokeTest() {}

	public static void main(String[] args) {
		for (int button : new int[] { ATTACK, USE, PICK_BLOCK }) {
			for (InteractionHand hand : InteractionHand.values()) {
				String click = "button " + button + " " + hand;

				InteractionKeyMappingTriggered meditating = new InteractionKeyMappingTriggered(button, null, hand);
				boolean cancelled = InputHandler.cancelVanillaClickWhileMeditating(meditating, true);
				check(cancelled && meditating.isCanceled(),
						"a vanilla click must be cancelled while meditating (" + click + ")");
				check(!meditating.shouldSwingHand(),
						"a vanilla click must not swing the hand while meditating (" + click + ")");

				InteractionKeyMappingTriggered standing = new InteractionKeyMappingTriggered(button, null, hand);
				boolean untouched = !InputHandler.cancelVanillaClickWhileMeditating(standing, false);
				check(untouched && !standing.isCanceled() && standing.shouldSwingHand(),
						"a vanilla click must stay untouched when the player does not meditate (" + click + ")");
			}
		}

		Path root = Path.of(System.getProperty("user.dir"));
		String inputHandler = read(root.resolve(
				"src/main/java/rotp/core/client/input/InputHandler.java")).replace("\r\n", "\n");
		int listener = inputHandler.indexOf(
				"\t@SubscribeEvent(priority = EventPriority.HIGHEST)\n"
				+ "\tpublic void cancelVanillaClickInMeditation(InteractionKeyMappingTriggered event) {");
		check(listener >= 0,
				"InputHandler must listen for vanilla clicks at the highest priority to cancel them while meditating");
		int listenerEnd = inputHandler.indexOf("\n\t}\n", listener);
		String body = inputHandler.substring(listener, listenerEnd);
		int rule = body.indexOf("cancelVanillaClickWhileMeditating(event,");
		int player = body.indexOf("mc.player != null", rule);
		int hamon = body.indexOf("PlayerPower.getPowerData(mc.player, ModPlayerPowers.HAMON)", player);
		int state = body.indexOf(".map(HamonData::isMeditating).orElse(false)", hamon);
		check(rule >= 0 && player > rule && hamon > player && state > hamon,
				"the meditation click listener must feed the local player's Hamon meditation state to the cancel rule");
		check(!body.contains("isAttack()") && !body.contains("isUseItem()") && !body.contains("isPickBlock()"),
				"the meditation click listener must cancel every kind of vanilla click, as the 1.16 handler did");

		System.out.println("Meditation vanilla click smoke test passed");
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
