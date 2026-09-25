package rotp.core.client.ui.screen_jojomenu;

import java.util.List;

/**
 * Input rules of the controls editor (1.16 HudLayoutEditingScreen), kept free of client classes
 * so gametests can check them.
 */
public final class ControlSchemeEditorInput {
	private ControlSchemeEditorInput() {}

	public static final int KEY_ESCAPE = 256; // GLFW_KEY_ESCAPE
	public static final int MOUSE_LEFT = 0;
	public static final int MOUSE_RIGHT = 1;

	/**
	 * Capture of one custom key. A modifier pressed first waits for the key it modifies (1.16
	 * keyPressedEditingKeybind); released with no key after it, the modifier is bound on its own
	 * (NeoForge KeyBindsScreen).
	 */
	public static final class KeyCapture {
		public static final int NO_KEY = -1;
		private int modifierKey = NO_KEY;
		private int modifierScan;

		public void reset() {
			modifierKey = NO_KEY;
		}

		/** True while the capture keeps waiting: the pressed key is a modifier. The first one is remembered. */
		public boolean waitsAfterPress(int keyCode, int scanCode, boolean isModifier) {
			if (!isModifier) return false;
			if (modifierKey == NO_KEY) {
				modifierKey = keyCode;
				modifierScan = scanCode;
			}
			return true;
		}

		/** True when the remembered modifier is released before any other key. */
		public boolean bindsModifierOnRelease(int keyCode) {
			return modifierKey != NO_KEY && keyCode == modifierKey;
		}

		public int modifierKey() {
			return modifierKey;
		}

		public int modifierScan() {
			return modifierScan;
		}
	}

	public enum SlotKey { MOVE, BIND, PASS }

	/**
	 * 1.16 keyPressed on the editor hotbars: a hotbar number key moves the dragged or hovered slot to
	 * that place, any other key (not Escape, not a modifier) becomes a custom key of the hovered ability.
	 * @param numKey hotbar number key index, -1 for other keys
	 * @param moveLayoutSize slot count of the dragged or hovered slot's hotbar, -1 when there is none
	 */
	public static SlotKey slotKey(int numKey, int moveLayoutSize, boolean hoveredHasAbility, int keyCode, boolean isModifier) {
		if (numKey >= 0) {
			return moveLayoutSize > numKey ? SlotKey.MOVE : SlotKey.PASS;
		}
		if (hoveredHasAbility && keyCode != KEY_ESCAPE && !isModifier) {
			return SlotKey.BIND;
		}
		return SlotKey.PASS;
	}

	/** 1.16 mouseClicked on a slot: LMB drags, RMB hides, other buttons become a custom key. */
	public static boolean bindsMouseButton(int button) {
		return button != MOUSE_LEFT && button != MOUSE_RIGHT;
	}

	/** 1.16 ActionsHotbar#moveTo: the slot is taken out and put back at the index. */
	public static <T> boolean moveSlot(List<T> layout, int from, int to) {
		if (from < 0 || from >= layout.size() || to < 0 || to >= layout.size()) return false;
		if (from != to) {
			layout.add(to, layout.remove(from));
		}
		return true;
	}

	/**
	 * Scroll over a hovered slot moves the HUD selection of its hotbar (1.16 ActionsOverlayGui#scrollAction),
	 * skipping slots the HUD does not show. Returns the new index, or -1 when no slot can be selected.
	 */
	public static int cycleSlot(int current, boolean[] shown, boolean backwards) {
		int n = shown.length;
		int index = current;
		for (int attempts = 0; attempts < n; attempts++) {
			if (index < 0 || index >= n) {
				index = backwards ? n - 1 : 0;
			}
			else {
				index += backwards ? -1 : 1;
				if (index < 0) index = n - 1;
				else if (index >= n) index = 0;
			}
			if (shown[index]) return index;
		}
		return -1;
	}
}
