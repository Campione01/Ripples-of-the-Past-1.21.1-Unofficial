package rotp.core.gametest;

import rotp.core.client.input.controlscheme.ClientControlScheme.Bind;
import rotp.core.core.JojoMod;
import rotp.core.powersystem.ability.controls.ControlSchemeSettings.KeyActiveType;
import rotp.core.powersystem.ability.controls.ControlSchemeSettings.OnKeyPress;
import rotp.core.powersystem.ability.controls.InputMethod;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 ActionsOverlayGui#updateHotkeyUi listed a custom key when isVisibleInHud() and
 * getHudInteraction().canTrigger(its HUD open), whatever its OnKeyPress. The port's HUD list needed
 * PERFORM, so SELECT keys and the other powers' OUTSIDE_HUD / ALWAYS keys never showed.
 * Only dist-neutral Bind state is used here (no key objects).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CustomKeyHudListGameTests {
	private CustomKeyHudListGameTests() {}

	private static Bind custom(OnKeyPress onKeyPress, KeyActiveType activeType, boolean visibleInHud) {
		Bind bind = new Bind(null, InputMethod.CLICK, null);
		bind.custom = true;
		bind.onKeyPress = onKeyPress;
		bind.activeType = activeType;
		bind.visibleInHud = visibleInHud;
		return bind;
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void selectKeysShowInOpenHud(GameTestHelper helper) {
		Bind select = custom(OnKeyPress.SELECT, KeyActiveType.INSIDE_HUD, true);
		helper.assertTrue(select.listedIn(true, true), "a visible SELECT key must be in the open HUD's key list");
		helper.assertTrue(!select.listedIn(true, false), "a SELECT key must stay out of the perform-on-press input map");

		Bind hidden = custom(OnKeyPress.SELECT, KeyActiveType.INSIDE_HUD, false);
		helper.assertTrue(!hidden.listedIn(true, true), "HUD '-' must hide a SELECT key");

		Bind perform = custom(OnKeyPress.PERFORM, KeyActiveType.INSIDE_HUD, true);
		helper.assertTrue(perform.listedIn(true, true) && perform.listedIn(true, false), "a PERFORM key is listed and performs");
		Bind performHidden = custom(OnKeyPress.PERFORM, KeyActiveType.INSIDE_HUD, false);
		helper.assertTrue(!performHidden.listedIn(true, true) && performHidden.listedIn(true, false),
				"a hidden PERFORM key still performs but is not shown");

		Bind outside = custom(OnKeyPress.PERFORM, KeyActiveType.OUTSIDE_HUD, true);
		helper.assertTrue(!outside.listedIn(true, true), "an OUTSIDE_HUD key is not shown in its own open HUD");

		Bind template = new Bind(null, InputMethod.HOLD, null);
		helper.assertTrue(template.listedIn(true, true) && template.listedIn(true, false), "template keys show in their open HUD");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void closedHudKeysShowByActiveType(GameTestHelper helper) {
		for (OnKeyPress onKeyPress : OnKeyPress.values()) {
			helper.assertTrue(custom(onKeyPress, KeyActiveType.OUTSIDE_HUD, true).shownInHud(false),
					"a visible OUTSIDE_HUD " + onKeyPress + " key of a closed HUD must show");
			helper.assertTrue(custom(onKeyPress, KeyActiveType.ALWAYS, true).shownInHud(false),
					"a visible ALWAYS " + onKeyPress + " key of a closed HUD must show");
			helper.assertTrue(custom(onKeyPress, KeyActiveType.ALWAYS, true).shownInHud(true),
					"a visible ALWAYS " + onKeyPress + " key must show in its open HUD");
			helper.assertTrue(!custom(onKeyPress, KeyActiveType.INSIDE_HUD, true).shownInHud(false),
					"an INSIDE_HUD " + onKeyPress + " key of a closed HUD must not show");
			helper.assertTrue(!custom(onKeyPress, KeyActiveType.OUTSIDE_HUD, false).shownInHud(false),
					"HUD '-' must hide an OUTSIDE_HUD " + onKeyPress + " key");
		}
		helper.assertTrue(!new Bind(null, InputMethod.CLICK, null).shownInHud(false),
				"a closed HUD's template keys are not listed");
		helper.succeed();
	}
}
