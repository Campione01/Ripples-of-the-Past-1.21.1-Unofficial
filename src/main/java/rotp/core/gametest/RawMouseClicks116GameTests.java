package rotp.core.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import rotp.core.client.input.RawMouseClicks;
import rotp.core.core.JojoMod;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The client's raw mouse clicks (client item W60-9). 1.16's InputHandler (:343-349) did its LMB+RMB pose only when both
 * buttons were clicked in the same client tick and had no path that replayed a vanilla click as a press, so a lone left
 * click always reached the vanilla attack click (rotp-sex-pistols' revolver closes its cylinder on it). The port's pose
 * asked whether the right button was still held, and a vanilla click whose raw press and release both came before the
 * tick was replayed as a press no raw release let go, so every later left press was taken for the pose and cancelled.
 * InputHandler and StandVanillaClickInput cannot be loaded here: RawMouseClicks, which holds the rules, is asked, and
 * their sources are read for its use.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RawMouseClicks116GameTests {
	private static final String LMB = "lmb";
	private static final String RMB = "rmb";

	private RawMouseClicks116GameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void theLmbRmbPoseTakesOnlyBothButtonsPressedInOneTickAsIn116(GameTestHelper helper) {
		RawMouseClicks<String> clicks = new RawMouseClicks<>();
		clicks.rawPress(RMB);
		helper.assertTrue(clicks.pressedThisTick(RMB) && !clicks.pressedThisTick(LMB),
				"1.16 posed for a left click in the tick of a right click: the right press is not counted in its tick");
		clicks.endTick();
		helper.assertTrue(!clicks.pressedThisTick(RMB),
				"1.16 posed only for both buttons clicked in one tick: a right press of an earlier tick, held or let go,"
						+ " still counts once that tick has ended");
		clicks.rawPress(LMB);
		clicks.rawRelease(LMB);
		helper.assertTrue(clicks.pressedThisTick(LMB),
				"a press and release before the tick are both clicks of that tick (1.16 clickCount): the press is forgotten");

		String handler = source(helper, "client/input/InputHandler.java");
		String pose = body(helper, handler, "private boolean handleDefaultJojoLmbRmb(");
		helper.assertTrue(pose.contains("rawMouseClicks.pressedThisTick(RMB) && doTheThing()")
				&& pose.contains("rawMouseClicks.pressedThisTick(LMB) && doTheThing()") && !pose.contains("_heldKeys"),
				"1.16 did the LMB+RMB pose only for both clicks of one tick: InputHandler.handleDefaultJojoLmbRmb does not ask"
						+ " RawMouseClicks.pressedThisTick of the other button: " + pose);
		String mouse = body(helper, handler, "public void handleMouseInput(");
		helper.assertTrue(mouse.contains("case InputConstants.PRESS -> rawMouseClicks.rawPress(button);")
				&& mouse.contains("case InputConstants.RELEASE -> rawMouseClicks.rawRelease(button);")
				&& mouse.indexOf("rawMouseClicks.rawPress(button)") < mouse.indexOf("handleInputEvent(button"),
				"InputHandler.handleMouseInput does not note a raw press before it handles it: " + mouse);
		String tickEnd = body(helper, handler, "public void handleKeyBindingsPost(");
		helper.assertTrue(tickEnd.contains("for (ClientKey replayed : rawMouseClicks.endTick()) {")
				&& tickEnd.contains("input(replayed, InputConstants.RELEASE, 0);"),
				"InputHandler.handleKeyBindingsPost does not end the tick's raw clicks and let the replayed presses go: " + tickEnd);
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void aVanillaClickIsReplayedOnlyWithoutARawPressAndLetGoOnceItsKeyIsUp(GameTestHelper helper) {
		RawMouseClicks<String> clicks = new RawMouseClicks<>();
		helper.assertTrue(clicks.replaysVanillaClick(RMB),
				"a vanilla use click with no raw press of the right button (a key bound to it) is no longer replayed");
		clicks.rawPress(RMB);
		clicks.rawRelease(RMB);
		helper.assertTrue(!clicks.replaysVanillaClick(RMB),
				"a vanilla use click whose raw press and release came before the tick is replayed as a press: it presses the"
						+ " ability again and keeps a held right button that no raw release lets go");
		clicks.endTick();
		helper.assertTrue(clicks.replaysVanillaClick(RMB), "the raw press of an ended tick still keeps a later click from replaying");

		boolean[] down = { true };
		clicks.replayed(RMB, () -> down[0]);
		List<String> held = clicks.endTick();
		down[0] = false;
		List<String> letGo = clicks.endTick();
		List<String> after = clicks.endTick();
		helper.assertTrue(held.isEmpty() && letGo.equals(List.of(RMB)) && after.isEmpty(),
				"a replayed press is let go once, at the end of the tick its key mapping is up: while down " + held
						+ ", once up " + letGo + ", after " + after);
		clicks.replayed(LMB, () -> false);
		clicks.rawRelease(LMB);
		helper.assertTrue(clicks.endTick().isEmpty(),
				"a raw release of the button did not drop its replayed press, which the tick's end lets go a second time");

		String vanilla = source(helper, "client/input/StandVanillaClickInput.java");
		String replay = body(helper, vanilla, "private static boolean handleVanillaMappedAbilityInput(");
		int guard = replay.indexOf("if (!inputHandler.rawMouseClicks.replaysVanillaClick(key)");
		int press = replay.indexOf("inputHandler.input(key, InputConstants.PRESS, 0)");
		helper.assertTrue(guard >= 0 && press > guard && replay.indexOf("inputHandler.rawMouseClicks.replayed(key, mapping::isDown);") > press,
				"StandVanillaClickInput.handleVanillaMappedAbilityInput replays a vanilla click without asking"
						+ " RawMouseClicks.replaysVanillaClick, or does not leave the replayed press to be let go: " + replay);
		helper.succeed();
	}

	private static String source(GameTestHelper helper, String file) {
		for (Path at = Path.of("").toAbsolutePath(); at != null; at = at.getParent()) {
			Path path = at.resolve("src/main/java/rotp/core").resolve(file);
			if (Files.isRegularFile(path)) {
				try {
					return Files.readString(path).replace("\r\n", "\n");
				}
				catch (IOException e) {
					throw new IllegalStateException("Could not read " + path, e);
				}
			}
		}
		helper.fail("Fixture: " + file + " not found above " + Path.of("").toAbsolutePath());
		return "";
	}

	private static String body(GameTestHelper helper, String source, String signature) {
		int start = source.indexOf(signature);
		int end = start >= 0 ? source.indexOf("\n\t}\n", start) : -1;
		helper.assertTrue(start >= 0 && end > start, "Fixture: " + signature + " not found");
		return source.substring(start, end);
	}
}
