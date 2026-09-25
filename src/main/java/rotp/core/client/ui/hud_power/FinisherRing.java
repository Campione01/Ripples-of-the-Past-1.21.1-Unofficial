package rotp.core.client.ui.hud_power;

import rotp.core.compat.v1_21_4.missingmethods.ARGB;
import rotp.core.core.JojoMod;

import net.minecraft.resources.ResourceLocation;

/**
 * Textures and tint of the Stand finisher ring drawn by {@link PowerHud.Finisher}.
 * 1.16 ActionsOverlayGui:1738-1740 drew the fill white (overlay.png u=114) at every value,
 * green (u=132) only when the heavy punch would be the finisher variation.
 * Free of client classes so gametests can load it.
 */
public final class FinisherRing {
	private FinisherRing() {}

	// stand_finisher_1 is white; 2 and 3 are baked green
	public static final ResourceLocation[] BARS = {
			JojoMod.resLoc("textures/hud/stand_finisher_1.png"),
			JojoMod.resLoc("textures/hud/stand_finisher_2.png"),
			JojoMod.resLoc("textures/hud/stand_finisher_3.png")
	};
	// baked green (0,255,33): a white tint cannot turn them white
	public static final ResourceLocation[] BARS_FULL = {
			JojoMod.resLoc("textures/hud/stand_finisher_1_full.png"),
			JojoMod.resLoc("textures/hud/stand_finisher_2_full.png")
	};
	// 1.16 overlay.png fill at u=132 was green; matches stand_finisher_*_full, half alpha like the white ring
	public static final int HEAVY_FINISHER_COLOR = 0x8000FF21;
	public static final int PLAIN_COLOR = ARGB.white(0.5f);

	public static int tint(boolean heavyVariation) {
		return heavyVariation ? HEAVY_FINISHER_COLOR : PLAIN_COLOR;
	}

	/** Ring the fractional part of the meter is filled on. */
	public static ResourceLocation fillRing(int fullFinishers) {
		return BARS[Math.min(fullFinishers, BARS.length - 1)];
	}

	/** Completed ring under the fill (fullFinishers >= 1): green only when the heavy finisher will fire. */
	public static ResourceLocation fullRing(int fullFinishers, boolean heavyVariation) {
		return heavyVariation
				? BARS_FULL[Math.min(fullFinishers, BARS_FULL.length) - 1]
				: BARS[0];
	}
}
