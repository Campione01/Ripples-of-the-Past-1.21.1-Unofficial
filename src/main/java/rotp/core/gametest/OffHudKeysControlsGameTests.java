package rotp.core.gametest;

import rotp.core.client.ui.hud_power.PowerHudControlsElement.ShownKeys;
import rotp.core.core.JojoMod;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 ActionsOverlayGui#updateHotkeyUi ran with currentMode == null too, so the owned powers' visible
 * OUTSIDE_HUD / ALWAYS custom keys drew with no HUD open. The port's controls element drew nothing then.
 * Warnings and "selected" abilities stay tied to an open HUD (1.16 getSelectedEnabledActions).
 * Uses only the element's pure ShownKeys rule (strings stand in for control schemes).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OffHudKeysControlsGameTests {
	private OffHudKeysControlsGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void openHudListsItsOwnKeys(GameTestHelper helper) {
		ShownKeys<String> shown = ShownKeys.pick("open", () -> "noHud", s -> true);
		helper.assertTrue("open".equals(shown.scheme()), "an open HUD must list its own scheme, got " + shown.scheme());
		helper.assertTrue(shown.hudOpen(), "an open HUD keeps its warnings and selection");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void noHudStillListsOffHudKeys(GameTestHelper helper) {
		ShownKeys<String> shown = ShownKeys.pick(null, () -> "noHud", s -> "noHud".equals(s));
		helper.assertTrue("noHud".equals(shown.scheme()),
				"with no HUD open the owned powers' off-HUD custom keys must still draw, got " + shown.scheme());
		helper.assertTrue(!shown.hudOpen(), "with no HUD open there are no warnings and nothing counts as selected");

		ShownKeys<String> none = ShownKeys.pick(null, () -> "noHud", s -> false);
		helper.assertTrue(none.scheme() == null, "no HUD and no off-HUD keys: the element must not draw");
		helper.assertTrue(!none.hudOpen(), "no HUD open");
		helper.succeed();
	}
}
