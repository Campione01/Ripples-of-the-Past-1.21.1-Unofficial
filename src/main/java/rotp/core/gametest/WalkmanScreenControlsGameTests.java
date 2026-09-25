package rotp.core.gametest;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import rotp.core.client.ui.screen.walkman.WalkmanScreenLayout;
import rotp.core.core.JojoMod;
import rotp.core.item.cassette.WalkmanPlaybackMode;

import net.minecraft.ChatFormatting;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 walkman screen: textured WalkmanButton keys (no label, tooltip only while active and hovered),
 * an 8x15 loop lever that drops 9 px in loop mode, "Rewind" on the first track, red ERROR for a missing
 * track, and left-drag on the volume wheel. The port used labelled vanilla buttons and never forwarded drags.
 * Screen classes are client-only, so their bytecode is read as a resource instead of being loaded.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WalkmanScreenControlsGameTests {
	private WalkmanScreenControlsGameTests() {}

	private static final String PKG = "rotp/core/client/ui/screen/walkman/";
	private static final String LAYOUT = PKG + "WalkmanScreenLayout.";
	private static final String BUTTON = PKG + "WalkmanButton";

	@GameTest(template = "empty")
	public static void walkmanKeysUse116LayoutAndSprites(GameTestHelper helper) {
		helper.assertTrue(WalkmanScreenLayout.REWIND_X == 39 && WalkmanScreenLayout.PLAY_X == 58
				&& WalkmanScreenLayout.FAST_FORWARD_X == 104 && WalkmanScreenLayout.STOP_X == 131
				&& WalkmanScreenLayout.KEY_Y == 107, "walkman key positions differ from 1.16 (39/58/104/131, y 107)");
		helper.assertTrue(WalkmanScreenLayout.SMALL_KEY_WIDTH == 14 && WalkmanScreenLayout.WIDE_KEY_WIDTH == 41
				&& WalkmanScreenLayout.KEY_HEIGHT == 13, "walkman key sizes differ from 1.16 (14x13, 41x13)");
		helper.assertTrue(WalkmanScreenLayout.keyTexY(true, true) == 240, "active hovered key must use sprite row 240");
		helper.assertTrue(WalkmanScreenLayout.keyTexY(true, false) == 227, "idle key must use sprite row 227");
		helper.assertTrue(WalkmanScreenLayout.keyTexY(false, true) == 227, "inactive key must not light up on hover");
		helper.assertTrue(WalkmanScreenLayout.showsTooltip(true, true), "active hovered key must show its tooltip");
		helper.assertFalse(WalkmanScreenLayout.showsTooltip(false, true), "inactive key must not show a tooltip");
		helper.assertFalse(WalkmanScreenLayout.showsTooltip(true, false), "key shows a tooltip without hover");

		helper.assertTrue(WalkmanScreenLayout.LEVER_X == 175 && WalkmanScreenLayout.LEVER_WIDTH == 8
				&& WalkmanScreenLayout.LEVER_HEIGHT == 15 && WalkmanScreenLayout.LEVER_TEX_Y == 235,
				"loop lever bounds or sprite row differ from 1.16 (x 175, 8x15, v 235)");
		helper.assertTrue(WalkmanScreenLayout.leverY(0, WalkmanPlaybackMode.STOP_AT_THE_END) == 107,
				"lever must sit at y 107 without loop");
		helper.assertTrue(WalkmanScreenLayout.leverY(0, WalkmanPlaybackMode.LOOP) == 116,
				"lever must drop 9 px in loop mode");
		helper.assertTrue(WalkmanScreenLayout.leverTexX(true) == 175 && WalkmanScreenLayout.leverTexX(false) == 183,
				"lever sprite must be u 175 hovered, 183 idle");

		helper.assertTrue("walkman.button.rewind.start".equals(WalkmanScreenLayout.rewindKey(true)),
				"rewind on the first track must say walkman.button.rewind.start");
		helper.assertTrue("walkman.button.rewind".equals(WalkmanScreenLayout.rewindKey(false)),
				"rewind to an earlier track must say walkman.button.rewind");
		Component error = WalkmanScreenLayout.missingTrackName();
		helper.assertTrue("ERROR".equals(error.getString()), "missing track name must read ERROR");
		helper.assertTrue(TextColor.fromLegacyFormat(ChatFormatting.RED).equals(error.getStyle().getColor())
				&& error.getStyle().isBold(), "missing track name must be red and bold");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void walkmanScreenDrawsWalkmanKeysNotVanillaButtons(GameTestHelper helper) {
		Set<String> screen = methodRefs(helper, PKG + "WalkmanScreen");
		helper.assertTrue(screen.contains(BUTTON + ".<init>(IIIILjava/lang/Runnable;Ljava/util/function/Supplier;I)V"),
				"WalkmanScreen does not build WalkmanButton keys");
		for (String ref : screen) {
			helper.assertFalse(ref.startsWith("net/minecraft/client/gui/components/Button."),
					"WalkmanScreen still builds vanilla buttons: " + ref);
			helper.assertFalse(ref.contains(".setMessage("), "WalkmanScreen still sets button labels: " + ref);
		}
		helper.assertTrue(screen.contains(BUTTON + ".showsTooltip()Z")
				&& screen.contains("net/minecraft/client/gui/GuiGraphics.renderTooltip(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;II)V"),
				"WalkmanScreen does not draw key tooltips on hover");
		helper.assertTrue(screen.contains(LAYOUT + "leverY(ILrotp/core/item/cassette/WalkmanPlaybackMode;)I")
				&& screen.contains(BUTTON + ".setY(I)V"), "loop lever does not follow the playback mode");
		helper.assertTrue(screen.contains(LAYOUT + "rewindKey(Z)Ljava/lang/String;"), "rewind tooltip ignores the first-track case");
		helper.assertTrue(screen.contains(LAYOUT + "missingTrackName()Lnet/minecraft/network/chat/Component;"),
				"missing track name is not the red ERROR");

		Set<String> lever = methodRefs(helper, PKG + "WalkmanScreen$1");
		helper.assertTrue(lever.contains(LAYOUT + "leverTexX(Z)I"), "loop lever does not draw the lever sprite");

		Set<String> button = methodRefs(helper, BUTTON);
		helper.assertTrue(button.contains(LAYOUT + "keyTexY(ZZ)I"), "WalkmanButton does not pick the key sprite row");
		helper.assertTrue(button.contains(LAYOUT + "showsTooltip(ZZ)Z"), "WalkmanButton tooltip is not gated on active+hovered");
		helper.assertTrue(button.contains("net/minecraft/client/resources/sounds/SimpleSoundInstance.forUI(Lnet/minecraft/sounds/SoundEvent;FF)Lnet/minecraft/client/resources/sounds/SimpleSoundInstance;"),
				"WalkmanButton lost the 1.16 click sound");
		for (String ref : button) {
			helper.assertFalse(ref.endsWith(".renderWidget(Lnet/minecraft/client/gui/GuiGraphics;IIF)V") || ref.contains(".renderString("),
					"WalkmanButton still draws the vanilla button or its label: " + ref);
		}
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void walkmanVolumeWheelTakesMouseDrag(GameTestHelper helper) {
		helper.assertTrue(WalkmanScreenLayout.forwardsDrag(true, true, 0), "left drag must reach the focused wheel");
		helper.assertFalse(WalkmanScreenLayout.forwardsDrag(false, true, 0), "drag forwarded with nothing focused");
		helper.assertFalse(WalkmanScreenLayout.forwardsDrag(true, false, 0), "drag forwarded without a held click");
		helper.assertFalse(WalkmanScreenLayout.forwardsDrag(true, true, 1), "right drag must not move the wheel");
		helper.assertTrue(WalkmanScreenLayout.wheelTexX(true) == 229 && WalkmanScreenLayout.wheelTexX(false) == 245,
				"wheel sprite must be u 229 hovered, 245 idle");

		Set<String> screen = methodRefs(helper, PKG + "WalkmanScreen");
		helper.assertTrue(screen.contains(LAYOUT + "forwardsDrag(ZZI)Z")
				&& screen.contains("net/minecraft/client/gui/components/events/GuiEventListener.mouseDragged(DDIDD)Z"),
				"WalkmanScreen does not pass mouse drags to the focused widget");
		helper.assertTrue(screen.contains("net/minecraft/client/gui/screens/inventory/AbstractContainerScreen.mouseDragged(DDIDD)Z"),
				"WalkmanScreen.mouseDragged no longer falls back to slot dragging");

		Set<String> wheel = methodRefs(helper, PKG + "WalkmanVolumeWheel");
		helper.assertTrue(wheel.contains(LAYOUT + "wheelTexX(Z)I"), "wheel sprite choice moved off the layout rule");
		for (String ref : wheel) {
			helper.assertFalse(ref.endsWith(".isHoveredOrFocused()Z"), "wheel stays highlighted while focused: " + ref);
		}
		helper.succeed();
	}

	// owner.name+descriptor of every method ref in the class constant pool
	private static Set<String> methodRefs(GameTestHelper helper, String className) {
		String path = className + ".class";
		InputStream raw = WalkmanScreenControlsGameTests.class.getResourceAsStream("/" + path);
		if (raw == null) {
			raw = WalkmanScreenControlsGameTests.class.getClassLoader().getResourceAsStream(path);
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
