package rotp.core.gametest;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import rotp.core.client.ui.screen_jojomenu.ControlSchemeEditorInput;
import rotp.core.client.ui.screen_jojomenu.ControlSchemeEditorInput.KeyCapture;
import rotp.core.client.ui.screen_jojomenu.ControlSchemeEditorInput.SlotKey;
import rotp.core.core.JojoMod;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 HudLayoutEditingScreen: a modifier pressed during key capture waited for the key it modifies,
 * number keys moved the hovered or dragged slot, other keys and mouse buttons over a slot became custom
 * keys of its ability, and scrolling over a slot moved the HUD selection. The port ended capture on the
 * modifier and ignored keys, extra buttons and scrolling over slots. The screen is client-only, so its
 * bytecode is read as a resource.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ControlSchemeEditorInputGameTests {
	private ControlSchemeEditorInputGameTests() {}

	private static final int KEY_X = 88;
	private static final int KEY_LEFT_CONTROL = 341;
	private static final int KEY_LEFT_SHIFT = 340;
	private static final String SCREEN = "rotp/core/client/ui/screen_jojomenu/ControlSchemeScreen";
	private static final String INPUT = "rotp/core/client/ui/screen_jojomenu/ControlSchemeEditorInput";

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void modifierKeepsKeyCaptureOpen(GameTestHelper helper) {
		KeyCapture capture = new KeyCapture();
		helper.assertTrue(capture.waitsAfterPress(KEY_LEFT_CONTROL, 29, true), "pressing Ctrl must keep the capture open");
		helper.assertTrue(capture.modifierKey() == KEY_LEFT_CONTROL && capture.modifierScan() == 29,
				"the first modifier pressed must be remembered");
		helper.assertTrue(capture.waitsAfterPress(KEY_LEFT_SHIFT, 42, true), "a second modifier must keep waiting");
		helper.assertTrue(capture.modifierKey() == KEY_LEFT_CONTROL, "a later modifier must not replace the first one");
		helper.assertFalse(capture.bindsModifierOnRelease(KEY_LEFT_SHIFT), "releasing another modifier must not bind it");
		helper.assertFalse(capture.waitsAfterPress(KEY_X, 45, false), "X after Ctrl must be bound (Ctrl + X)");

		KeyCapture bare = new KeyCapture();
		helper.assertFalse(bare.bindsModifierOnRelease(KEY_LEFT_CONTROL), "nothing to bind before a modifier press");
		bare.waitsAfterPress(KEY_LEFT_CONTROL, 29, true);
		helper.assertTrue(bare.bindsModifierOnRelease(KEY_LEFT_CONTROL), "Ctrl released alone must be bound on its own");
		bare.reset();
		helper.assertTrue(bare.modifierKey() == KeyCapture.NO_KEY, "reset must forget the modifier");
		helper.assertFalse(bare.bindsModifierOnRelease(KEY_LEFT_CONTROL), "a reset capture must not bind a stale modifier");

		KeyCapture plain = new KeyCapture();
		helper.assertFalse(plain.waitsAfterPress(KEY_X, 45, false), "a plain key must be bound at once");
		helper.assertTrue(plain.modifierKey() == KeyCapture.NO_KEY, "a plain key is not a modifier");

		Set<String> screen = methodRefs(helper, SCREEN);
		helper.assertTrue(screen.contains(INPUT + "$KeyCapture.waitsAfterPress(IIZ)Z"),
				"ControlSchemeScreen key capture ignores modifier presses");
		helper.assertTrue(screen.contains(INPUT + "$KeyCapture.bindsModifierOnRelease(I)Z"),
				"ControlSchemeScreen never binds a modifier released on its own");
		helper.assertTrue(screen.contains("net/neoforged/neoforge/client/settings/KeyModifier.getKeyModifier(Lcom/mojang/blaze3d/platform/InputConstants$Key;)Lnet/neoforged/neoforge/client/settings/KeyModifier;"),
				"captured key does not take the modifier pressed during capture");
		helper.assertTrue(screen.contains(SCREEN + ".capturedModifier()Lnet/neoforged/neoforge/client/settings/KeyModifier;"),
				"key and mouse capture do not use the modifier pressed during capture");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void hotbarSlotKeysMoveAndBind(GameTestHelper helper) {
		helper.assertTrue(ControlSchemeEditorInput.slotKey(2, 5, true, 51, false) == SlotKey.MOVE,
				"hotbar key 3 over a 5-slot hotbar must move the slot");
		helper.assertTrue(ControlSchemeEditorInput.slotKey(4, 4, true, 53, false) == SlotKey.PASS,
				"hotbar key past the last slot must do nothing");
		helper.assertTrue(ControlSchemeEditorInput.slotKey(0, -1, false, 49, false) == SlotKey.PASS,
				"hotbar key with no hovered or dragged slot must do nothing");
		helper.assertTrue(ControlSchemeEditorInput.slotKey(-1, 5, true, KEY_X, false) == SlotKey.BIND,
				"X over a slot must become a custom key of its ability");
		helper.assertTrue(ControlSchemeEditorInput.slotKey(-1, 5, false, KEY_X, false) == SlotKey.PASS,
				"X with no hovered ability must do nothing");
		helper.assertTrue(ControlSchemeEditorInput.slotKey(-1, 5, true, ControlSchemeEditorInput.KEY_ESCAPE, false) == SlotKey.PASS,
				"Escape over a slot must still close the screen");
		helper.assertTrue(ControlSchemeEditorInput.slotKey(-1, 5, true, KEY_LEFT_CONTROL, true) == SlotKey.PASS,
				"a modifier over a slot must not be bound");

		List<String> layout = new ArrayList<>(List.of("a", "b", "c", "d"));
		helper.assertTrue(ControlSchemeEditorInput.moveSlot(layout, 0, 2) && layout.equals(List.of("b", "c", "a", "d")),
				"moving slot 1 to 3 must give b c a d, got " + layout);
		helper.assertTrue(ControlSchemeEditorInput.moveSlot(layout, 3, 0) && layout.equals(List.of("d", "b", "c", "a")),
				"moving slot 4 to 1 must give d b c a, got " + layout);
		helper.assertFalse(ControlSchemeEditorInput.moveSlot(layout, 1, 4), "moving past the end must be refused");

		helper.assertTrue(ControlSchemeEditorInput.bindsMouseButton(2), "middle click on a slot must bind the button");
		helper.assertTrue(ControlSchemeEditorInput.bindsMouseButton(3), "mouse 4 on a slot must bind the button");
		helper.assertFalse(ControlSchemeEditorInput.bindsMouseButton(0) || ControlSchemeEditorInput.bindsMouseButton(1),
				"LMB and RMB keep drag and hide");

		Set<String> screen = methodRefs(helper, SCREEN);
		helper.assertTrue(screen.contains(INPUT + ".slotKey(IIZIZ)L" + INPUT + "$SlotKey;")
				&& screen.contains(INPUT + ".moveSlot(Ljava/util/List;II)Z"),
				"ControlSchemeScreen ignores keys over the editor hotbars");
		helper.assertTrue(screen.contains(INPUT + ".bindsMouseButton(I)Z"),
				"ControlSchemeScreen ignores extra mouse buttons on slots");
		helper.assertTrue(screen.contains("rotp/core/client/input/controlscheme/AllControlSchemes.addCustomBind(Lrotp/core/client/input/controlscheme/ClientControlScheme;Lrotp/core/client/input/controlscheme/ClientControlScheme$MoveGroup;Ljava/lang/String;)Lrotp/core/client/input/controlscheme/ClientControlScheme$Bind;"),
				"ControlSchemeScreen cannot add custom keys");
		helper.assertTrue(screen.contains(SCREEN + ".keySlotAction(II)Z"), "keyPressed never tries the hovered slot");
		helper.assertTrue(screen.contains(SCREEN + ".bindSlotAbility(L" + SCREEN
				+ "$SlotPos;Lcom/mojang/blaze3d/platform/InputConstants$Key;)V"), "slot keys and buttons are never bound");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void scrollOverSlotMovesHudSelection(GameTestHelper helper) {
		boolean[] shown = { true, false, true };
		helper.assertTrue(ControlSchemeEditorInput.cycleSlot(0, shown, false) == 2, "scrolling down from slot 1 must skip the hidden 2");
		helper.assertTrue(ControlSchemeEditorInput.cycleSlot(2, shown, false) == 0, "scrolling down from the last slot must wrap");
		helper.assertTrue(ControlSchemeEditorInput.cycleSlot(0, shown, true) == 2, "scrolling up from slot 1 must wrap to the end");
		helper.assertTrue(ControlSchemeEditorInput.cycleSlot(-1, shown, false) == 0, "no selection scrolls to the first slot");
		helper.assertTrue(ControlSchemeEditorInput.cycleSlot(0, new boolean[] { true }, false) == 0, "a lone slot stays selected");
		helper.assertTrue(ControlSchemeEditorInput.cycleSlot(0, new boolean[] { false, false }, false) == -1,
				"nothing shown must select nothing");

		Set<String> screen = methodRefs(helper, SCREEN);
		helper.assertTrue(screen.contains(INPUT + ".cycleSlot(I[ZZ)I"),
				"ControlSchemeScreen scrolling over a slot does not move the HUD selection");
		helper.succeed();
	}

	// owner.name+descriptor of every method ref in the class constant pool
	private static Set<String> methodRefs(GameTestHelper helper, String className) {
		String path = className + ".class";
		InputStream raw = ControlSchemeEditorInputGameTests.class.getResourceAsStream("/" + path);
		if (raw == null) {
			raw = ControlSchemeEditorInputGameTests.class.getClassLoader().getResourceAsStream(path);
		}
		helper.assertTrue(raw != null, "Missing class file " + path);
		try (DataInputStream in = new DataInputStream(raw)) {
			helper.assertTrue(in.readInt() == 0xCAFEBABE, path + " is not a class file");
			in.readUnsignedShort();
			in.readUnsignedShort();
			int count = in.readUnsignedShort();
			String[] utf = new String[count];
			int[] a = new int[count];
			int[] b = new int[count];
			int[] tag = new int[count];
			for (int i = 1; i < count; i++) {
				tag[i] = in.readUnsignedByte();
				switch (tag[i]) {
				case 1 -> utf[i] = in.readUTF();
				case 7, 8, 16, 19, 20 -> a[i] = in.readUnsignedShort();
				case 3, 4 -> in.readInt();
				case 5, 6 -> { in.readLong(); i++; }
				case 9, 10, 11, 12, 17, 18 -> { a[i] = in.readUnsignedShort(); b[i] = in.readUnsignedShort(); }
				case 15 -> { in.readUnsignedByte(); a[i] = in.readUnsignedShort(); }
				default -> throw new IOException("Unknown constant tag " + tag[i]);
				}
			}
			Set<String> refs = new HashSet<>();
			for (int i = 1; i < count; i++) {
				if (tag[i] == 10 || tag[i] == 11) {
					String owner = utf[a[a[i]]];
					int nameAndType = b[i];
					refs.add(owner + "." + utf[a[nameAndType]] + utf[b[nameAndType]]);
				}
			}
			return refs;
		} catch (IOException e) {
			helper.fail("Cannot read " + path + ": " + e);
			return Set.of();
		}
	}
}
