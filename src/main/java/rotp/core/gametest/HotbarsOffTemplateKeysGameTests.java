package rotp.core.gametest;

import rotp.core.client.input.controlscheme.ClientControlScheme.Bind;
import rotp.core.core.JojoMod;
import rotp.core.powersystem.ability.controls.InputBindTemplate;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.controls.InputUseVanillaMapping;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 kept every stand action in the attack (LMB) and ability (RMB) hotbars; with hotbarsEnabled off
 * ActionsOverlayGui#getEnabledActions was empty and the click stayed vanilla. The port's Hotbars OFF only
 * emptied the X hotbar, so the template LMB/RMB binds still punched and still showed in the HUD.
 * Only dist-neutral Bind state is used here (no key objects).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HotbarsOffTemplateKeysGameTests {
	private HotbarsOffTemplateKeysGameTests() {}

	// as ClientControlScheme#create builds a template bind
	private static Bind template(InputBindTemplate input, InputMethod inputMethod) {
		Bind bind = new Bind(null, inputMethod, null);
		bind.attackOrUseButton = Bind.isAttackOrUseButton(input);
		return bind;
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void attackAndUseButtonsAreTheHotbarKeys(GameTestHelper helper) {
		helper.assertTrue(Bind.isAttackOrUseButton(InputKey.LMB), "LMB is the 1.16 attack hotbar key");
		helper.assertTrue(Bind.isAttackOrUseButton(InputKey.RMB), "RMB is the 1.16 ability hotbar key");
		helper.assertTrue(Bind.isAttackOrUseButton(new InputUseVanillaMapping("key.attack")),
				"the vanilla attack mapping is the attack hotbar key");
		helper.assertTrue(Bind.isAttackOrUseButton(new InputUseVanillaMapping("key.use")),
				"the vanilla use mapping is the ability hotbar key");
		helper.assertTrue(!Bind.isAttackOrUseButton(InputKey.MMB) && !Bind.isAttackOrUseButton(InputKey.X)
				&& !Bind.isAttackOrUseButton(null), "other keys are not hotbar keys");
		helper.assertTrue(!Bind.isAttackOrUseButton(new InputUseVanillaMapping("jojo_ripples.key.use_special_ability")),
				"a mod mapping is not a hotbar key");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void hotbarsOffDropsTemplateMouseBinds(GameTestHelper helper) {
		Bind punch = template(InputKey.LMB, InputMethod.CLICK);
		Bind guard = template(InputKey.RMB, InputMethod.HOLD);
		for (Bind bind : new Bind[] { punch, guard }) {
			helper.assertTrue(bind.listedIn(true, false, true) && bind.listedIn(true, true, true),
					"Hotbars ON: a template LMB/RMB bind fires and shows");
			helper.assertTrue(!bind.listedIn(true, false, false), "Hotbars OFF: a template LMB/RMB bind must not fire");
			helper.assertTrue(!bind.listedIn(true, true, false), "Hotbars OFF: a template LMB/RMB row must not show in the HUD");
		}

		Bind grab = template(InputKey.G, InputMethod.CLICK);
		helper.assertTrue(grab.listedIn(true, false, false) && grab.listedIn(true, true, false),
				"Hotbars OFF keeps template binds on other keys");

		Bind custom = template(InputKey.LMB, InputMethod.CLICK);
		custom.custom = true;
		helper.assertTrue(custom.listedIn(true, false, false) == custom.listedIn(true, false)
				&& custom.listedIn(true, true, false) == custom.listedIn(true, true)
				&& custom.listedIn(true, false, false),
				"Hotbars OFF leaves the player's own keys unchanged, LMB included");
		helper.succeed();
	}
}
