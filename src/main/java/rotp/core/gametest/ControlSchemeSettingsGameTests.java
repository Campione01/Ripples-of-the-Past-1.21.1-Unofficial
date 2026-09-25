package rotp.core.gametest;

import java.util.List;

import rotp.core.core.JojoMod;
import rotp.core.powersystem.ability.controls.ControlSchemeSettings;
import rotp.core.powersystem.ability.controls.ControlSchemeSettings.KeyActiveType;
import rotp.core.powersystem.ability.controls.ControlSchemeSettings.OnKeyPress;
import rotp.core.powersystem.ability.controls.InputMethod;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 saved each power type's controls customisation (HudControlSettings): hotbar order, hidden slots and
 * per-ability keys with OnKeyPress and KeyActiveType. The port had no editor and no save. These pin the
 * dist-neutral save format and merge rules the client controls editor uses.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ControlSchemeSettingsGameTests {
	private ControlSchemeSettingsGameTests() {}

	@GameTest(template = "empty")
	public static void savedHotbarOrderWinsAndNewSlotsAppend(GameTestHelper helper) {
		List<String> merged = ControlSchemeSettings.mergeOrder(
				List.of("punch", "barrage", "heavy", "block"), List.of("heavy", "punch", "removed_ability"));
		helper.assertTrue(merged.equals(List.of("heavy", "punch", "barrage", "block")),
				"Saved order must come first, removed abilities dropped, new ones appended: " + merged);
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void settingsJsonRoundTrip(GameTestHelper helper) {
		ControlSchemeSettings settings = new ControlSchemeSettings();
		settings.hotbarsEnabled = false;
		ControlSchemeSettings.GroupSettings group = new ControlSchemeSettings.GroupSettings();
		ControlSchemeSettings.HotbarLayout hotbar = new ControlSchemeSettings.HotbarLayout();
		hotbar.order.addAll(List.of("heavy", "punch"));
		hotbar.hidden.add("punch");
		group.hotbars.add(hotbar);
		ControlSchemeSettings.Keybind keybind = new ControlSchemeSettings.Keybind();
		keybind.ability = "heavy";
		keybind.key = "key.keyboard.g";
		keybind.modifier = "SHIFT";
		keybind.inputMethod = InputMethod.HOLD;
		keybind.onKeyPress = OnKeyPress.SELECT;
		keybind.activeType = KeyActiveType.OUTSIDE_HUD;
		keybind.visibleInHud = false;
		group.keybinds.add(keybind);
		settings.groups.put("stand", group);

		JsonObject json = JsonParser.parseString(settings.toJson().toString()).getAsJsonObject();
		ControlSchemeSettings read = ControlSchemeSettings.fromJson(json);
		helper.assertFalse(read.hotbarsEnabled, "Hotbars off must be saved");
		ControlSchemeSettings.GroupSettings readGroup = read.groups.get("stand");
		helper.assertTrue(readGroup != null && readGroup.hotbars.size() == 1 && readGroup.keybinds.size() == 1,
				"Group, hotbar and key must be saved: " + json);
		ControlSchemeSettings.HotbarLayout readHotbar = readGroup.hotbars.get(0);
		helper.assertTrue(readHotbar.order.equals(List.of("heavy", "punch")), "Hotbar order must be saved: " + readHotbar.order);
		helper.assertTrue(readHotbar.hidden.contains("punch") && readHotbar.hidden.size() == 1,
				"Hidden slots must be saved: " + readHotbar.hidden);
		ControlSchemeSettings.Keybind readKey = readGroup.keybinds.get(0);
		helper.assertTrue("heavy".equals(readKey.ability) && "key.keyboard.g".equals(readKey.key) && "SHIFT".equals(readKey.modifier),
				"Key ability, key and modifier must be saved: " + json);
		helper.assertTrue(readKey.inputMethod == InputMethod.HOLD && readKey.onKeyPress == OnKeyPress.SELECT
				&& readKey.activeType == KeyActiveType.OUTSIDE_HUD && !readKey.visibleInHud,
				"Key input, press mode, HUD mode and HUD icon must be saved: " + json);
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void keyActiveTypeFollowsHudState(GameTestHelper helper) {
		helper.assertTrue(KeyActiveType.ALWAYS.canTrigger(true) && KeyActiveType.ALWAYS.canTrigger(false), "ALWAYS works in both HUD states");
		helper.assertTrue(KeyActiveType.INSIDE_HUD.canTrigger(true) && !KeyActiveType.INSIDE_HUD.canTrigger(false), "INSIDE_HUD only with the HUD open");
		helper.assertTrue(!KeyActiveType.OUTSIDE_HUD.canTrigger(true) && KeyActiveType.OUTSIDE_HUD.canTrigger(false), "OUTSIDE_HUD only with the HUD closed");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void brokenKeyEntriesFallBackToDefaults(GameTestHelper helper) {
		String text = "{\"groups\":{\"g\":{\"keybinds\":["
				+ "{\"key\":\"key.keyboard.h\"},"
				+ "{\"ability\":\"punch\",\"input\":\"BOGUS\",\"onKeyPress\":\"BOGUS\",\"active\":\"BOGUS\"}]}}}";
		ControlSchemeSettings read = ControlSchemeSettings.fromJson(JsonParser.parseString(text).getAsJsonObject());
		helper.assertTrue(read.hotbarsEnabled, "Hotbars stay on when the file does not say");
		ControlSchemeSettings.GroupSettings group = read.groups.get("g");
		helper.assertTrue(group != null && group.keybinds.size() == 1, "A key without an ability is skipped");
		ControlSchemeSettings.Keybind keybind = group.keybinds.get(0);
		helper.assertTrue(ControlSchemeSettings.UNBOUND_KEY.equals(keybind.key) && "NONE".equals(keybind.modifier),
				"A missing key reads as unbound");
		helper.assertTrue(keybind.inputMethod == InputMethod.CLICK && keybind.onKeyPress == OnKeyPress.PERFORM
				&& keybind.activeType == KeyActiveType.INSIDE_HUD && keybind.visibleInHud,
				"Unknown modes fall back to the 1.16 defaults (perform, inside HUD, shown in HUD)");
		helper.succeed();
	}
}
