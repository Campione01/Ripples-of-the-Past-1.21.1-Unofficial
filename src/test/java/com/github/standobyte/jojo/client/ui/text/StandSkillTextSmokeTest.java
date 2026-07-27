package com.github.standobyte.jojo.client.ui.text;

import net.minecraft.network.chat.Component;

public final class StandSkillTextSmokeTest {
	private StandSkillTextSmokeTest() {}

	public static void run() {
		Object marker = new Object();
		Object[] formatted = StandSkillText.skinFormatArgs(new Object[] {
				Component.literal("Shift + Right Button"),
				marker
		});

		check("Shift + Right Button".equals(formatted[0]),
				"skin formatting must receive visible Component text");
		check(formatted[1] == marker,
				"non-Component skin formatting arguments must be preserved");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
