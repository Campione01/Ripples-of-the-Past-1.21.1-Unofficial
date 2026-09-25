package rotp.core.client.entityrender.stand;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import net.minecraft.util.Mth;

/**
 * 1.16 StarPlatinumModel.manualAnimateHair: a slow hair sway added on top of every pose.
 * Holds no client classes, so gametests can load it.
 */
public final class StandHairSway {
	private StandHairSway() {}

	/** The hair bones 1.16 swayed (hair1/3/5/7/9 stay still). */
	public static final List<String> STAR_PLATINUM_HAIR = List.of(
			"hair2", "hair4", "hair6", "hair8", "hair10",
			"hair11", "hair12", "hair13", "hair14", "hair15", "hair16", "hair17", "hair18", "hair19", "hair20",
			"hair21", "hair22", "hair23", "hair24", "hair25", "hair26", "hair27", "hair28", "hair29", "hair30",
			"hair31", "hair32", "hair33", "hair34", "hair35", "hair36", "hair37", "hair38", "hair39", "hair40",
			"hair41", "hair42", "hair43", "hair44", "hair45", "hair46", "hair47", "hair48");

	private static final float TWO_PI = (float) Math.PI * 2;
	private static final float X_AMPLITUDE = 0.05F;
	private static final float X_PERIOD_TICKS = 71F;
	private static final float Y_AMPLITUDE = 0.0125F;
	private static final float Y_PERIOD_TICKS = 31F;

	/** Looks up each swayed bone by name; missing ones are skipped (other Stands have none). */
	public static <T> List<T> collect(Function<String, Optional<T>> byName) {
		List<T> found = new ArrayList<>();
		for (String name : STAR_PLATINUM_HAIR) {
			byName.apply(name).ifPresent(found::add);
		}
		return found;
	}

	/** xRot offset in radians; the bone's pivot x shifts its phase. */
	public static float xRot(float ticks, float pivotX) {
		return X_AMPLITUDE * wave(ticks + pivotX, X_PERIOD_TICKS);
	}

	/** yRot offset in radians; the bone's pivot y shifts its phase. */
	public static float yRot(float ticks, float pivotY) {
		return Y_AMPLITUDE * wave(ticks + pivotY, Y_PERIOD_TICKS);
	}

	private static float wave(float t, float period) {
		float v = t / period;
		v -= (float) Math.floor(v);
		return Mth.sin(v * TWO_PI);
	}
}
