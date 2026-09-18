package rotp.core.client.standskin;

import net.minecraft.network.chat.Component;

public final class StandSkinFormatSmokeTest {
	private StandSkinFormatSmokeTest() {}

	public static void run() {
		Object marker = new Object();
		Object[] formatted = StandSkin.formatArgs(new Object[] {
				Component.literal("Shift + Right Button"),
				marker
		});

		check("Shift + Right Button".equals(formatted[0]),
				"skin formatting must receive visible Component text");
		check(formatted[1] == marker,
				"non-Component skin formatting arguments must be preserved");

		// the path every skin translation takes, ability names included: an entity or item name must not print
		// as its toString() - measured in game as "Stay with translation{key='entity.minecraft.cow', args=[]}"
		String name = StandSkin.formatTranslation("Stay with %s", Component.literal("Cow"));
		check("Stay with Cow".equals(name),
				"a Component argument must not reach String.format as its toString(): " + name);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
