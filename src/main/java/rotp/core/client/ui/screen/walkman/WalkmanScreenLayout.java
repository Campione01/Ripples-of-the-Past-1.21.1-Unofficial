package rotp.core.client.ui.screen.walkman;

import rotp.core.item.cassette.WalkmanPlaybackMode;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * 1.16 walkman screen control layout, sprite rows and input rules.
 * Kept free of client classes so gametests can call it.
 */
public final class WalkmanScreenLayout {
	private WalkmanScreenLayout() {}

	// cassette keys, relative to the screen corner
	public static final int KEY_Y = 107;
	public static final int KEY_HEIGHT = 13;
	public static final int SMALL_KEY_WIDTH = 14;
	public static final int WIDE_KEY_WIDTH = 41;
	public static final int REWIND_X = 39;
	public static final int PLAY_X = 58;
	public static final int FAST_FORWARD_X = 104;
	public static final int STOP_X = 131;
	// key sprites use their own x as the texture x
	public static final int KEY_TEX_Y = 227;
	public static final int KEY_TEX_Y_HOVERED = 240;

	// loop lever
	public static final int LEVER_X = 175;
	public static final int LEVER_WIDTH = 8;
	public static final int LEVER_HEIGHT = 15;
	public static final int LEVER_STEP = 9;
	public static final int LEVER_TEX_Y = 235;

	public static int keyTexY(boolean active, boolean hovered) {
		return active && hovered ? KEY_TEX_Y_HOVERED : KEY_TEX_Y;
	}

	public static boolean showsTooltip(boolean active, boolean hovered) {
		return active && hovered;
	}

	public static int leverTexX(boolean hovered) {
		return hovered ? 175 : 183;
	}

	// the lever sits 9 px lower when looping
	public static int leverY(int topPos, WalkmanPlaybackMode mode) {
		return topPos + KEY_Y + mode.ordinal() * LEVER_STEP;
	}

	public static String rewindKey(boolean restartsCurrentTrack) {
		return restartsCurrentTrack ? "walkman.button.rewind.start" : "walkman.button.rewind";
	}

	public static Component missingTrackName() {
		return Component.literal("ERROR").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
	}

	// left-button drags go to the focused widget (the volume wheel), as in 1.16
	public static boolean forwardsDrag(boolean hasFocused, boolean dragging, int button) {
		return hasFocused && dragging && button == 0;
	}

	// wheel highlight follows the mouse only, not click focus
	public static int wheelTexX(boolean mouseOver) {
		return mouseOver ? 229 : 245;
	}
}
