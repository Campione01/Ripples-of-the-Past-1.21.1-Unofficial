package com.github.standobyte.jojo.powersystem.ability.input;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class InputKeyId {
	private static final int DEVICE_TYPE_MASK = 3;
	private static final int MOUSE_TYPE_ORDINAL = 2;
	private static final int RIGHT_MOUSE_BUTTON = 1;

	public static final short STAND_ITEM_RMB = keyboardMouse(
			MOUSE_TYPE_ORDINAL, RIGHT_MOUSE_BUTTON);

	private InputKeyId() {}

	public static short keyboardMouse(int typeOrdinal, int keyCode) {
		return (short) ((typeOrdinal & DEVICE_TYPE_MASK) | (keyCode << 2));
	}
}
