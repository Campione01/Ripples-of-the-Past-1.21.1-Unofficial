package rotp.core.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import rotp.core.core.JojoMod;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * StandStatsRenderer.standStatsPower keeps the Stand power of the last pause screen, and with it that power's player
 * and client level. Nothing else let it go: closing the screen does not (its crutch flag is always set), so after
 * "Save and Quit" the old world stayed in memory until the next world's pause screen. It is let go when the client
 * leaves the world and when its player is replaced (respawn, another dimension). The client class cannot be loaded
 * here: this reads its source.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandStatsPowerReleaseClientGameTests {
	private static final String FILE = "src/main/java/rotp/core/client/ui/StandStatsRenderer.java";
	private static final String RELEASE = "standStatsPower=null;";

	private StandStatsPowerReleaseClientGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void thePauseScreensStandPowerIsLetGoWhenTheClientLeavesTheWorldOrGetsANewPlayer(GameTestHelper helper) {
		String source = null;
		for (Path at = Path.of("").toAbsolutePath(); at != null && source == null; at = at.getParent()) {
			Path file = at.resolve(FILE);
			if (Files.isRegularFile(file)) {
				try {
					source = Files.readString(file);
				}
				catch (IOException e) {
					throw new IllegalStateException("Could not read " + file, e);
				}
			}
		}
		helper.assertTrue(source != null, "Fixture: StandStatsRenderer.java not found above " + Path.of("").toAbsolutePath());
		String code = source.replaceAll("//[^\\n]*", "").replaceAll("\\s+", "");
		helper.assertTrue(code.contains("@EventBusSubscriber(modid=JojoMod.MOD_ID,value=Dist.CLIENT)"),
				"Fixture: StandStatsRenderer is no longer a client event subscriber, so its handlers would not run");
		helper.assertTrue(code.contains("publicstaticStandPowerstandStatsPower;"),
				"Fixture: StandStatsRenderer.standStatsPower is gone or changed its type");
		for (String event : new String[] { "ClientPlayerNetworkEvent.LoggingOut", "ClientPlayerNetworkEvent.Clone" }) {
			String handler = handlerOf(code, event);
			helper.assertTrue(handler != null, "StandStatsRenderer has no @SubscribeEvent handler of " + event
					+ ", so the pause screen's Stand power keeps the old player and client level in memory");
			helper.assertTrue(handler.contains(RELEASE), "StandStatsRenderer's " + event
					+ " handler does not let standStatsPower go, so the old player and client level stay in memory");
		}
		helper.succeed();
	}

	// the body of the first @SubscribeEvent public static method of the class that takes the event, or null
	private static String handlerOf(String code, String event) {
		for (int at = code.indexOf("@SubscribeEvent"); at >= 0; at = code.indexOf("@SubscribeEvent", at + 1)) {
			int open = code.indexOf('{', at);
			if (open < 0) {
				return null;
			}
			String head = code.substring(at, open);
			if (!head.contains("publicstaticvoid") || !head.contains("(" + event)) {
				continue;
			}
			int depth = 0;
			for (int i = open; i < code.length(); i++) {
				char c = code.charAt(i);
				if (c == '{') depth++;
				else if (c == '}' && --depth == 0) {
					return code.substring(open, i + 1);
				}
			}
			throw new IllegalStateException("Unbalanced braces after " + head);
		}
		return null;
	}
}
