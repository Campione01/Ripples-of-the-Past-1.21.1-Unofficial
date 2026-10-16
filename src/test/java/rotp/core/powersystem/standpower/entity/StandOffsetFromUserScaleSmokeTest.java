package rotp.core.powersystem.standpower.entity;

import net.minecraft.world.phys.Vec3;

/**
 * 1.16 StandRelativeOffset.getAbsoluteVec scaled an offset by its user's bounding box against its type's size: across
 * by the width and up by the height, in whatever pose the user is.
 */
public final class StandOffsetFromUserScaleSmokeTest {
	private static final float PLAYER_WIDTH = 0.6F;
	private static final float PLAYER_HEIGHT = 1.8F;
	private static final double EPSILON = 1.0E-6;

	private StandOffsetFromUserScaleSmokeTest() {}

	public static void main(String[] args) {
		// a baby's age, the scale attribute and a size mod all come through the bounding box, once
		checkUniformScale(1);
		checkUniformScale(0.5F);
		checkUniformScale(0.15F);
		checkUniformScale(2);
		checkUniformScale(0.5F * 2 * 0.15F);

		checkPoses();
		checkThe116Values();

		checkClose(0, scale(new Vec3(1, 1, 1), 0, PLAYER_HEIGHT).x,
				"a zero width puts the Stand over its user, as 1.16's ratio did");
		checkClose(1, scale(new Vec3(1, 1, 1), Float.NaN, PLAYER_HEIGHT).x,
				"a NaN width must not corrupt the position");
		checkClose(1, scale(new Vec3(1, 1, 1), Float.POSITIVE_INFINITY, PLAYER_HEIGHT).x,
				"an infinite width must not corrupt the position");
		checkClose(1, StandOffsetFromUser.scaleOffsetForUser(new Vec3(1, 1, 1), PLAYER_WIDTH, PLAYER_HEIGHT, 0, PLAYER_HEIGHT).x,
				"a zero default width must not corrupt the position");
		System.out.println("StandOffsetFromUserScaleSmokeTest: PASS");
	}

	private static void checkUniformScale(float scale) {
		for (Vec3 offset : offsets()) {
			Vec3 actual = scale(offset, PLAYER_WIDTH * scale, PLAYER_HEIGHT * scale);
			checkVec(offset.scale(scale), actual, "a user " + scale + " times the type's size");
			Vec3 restored = scale(offset, PLAYER_WIDTH, PLAYER_HEIGHT);
			checkVec(offset, restored, "a user of the type's size must not compound the scale");
		}
	}

	// Player.POSES and LivingEntity.SLEEPING_DIMENSIONS
	private static void checkPoses() {
		for (Vec3 offset : offsets()) {
			checkVec(offset, scale(offset, 0.6F, 1.8F), "standing");
			checkVec(new Vec3(offset.x, offset.y * 1.5 / 1.8, offset.z), scale(offset, 0.6F, 1.5F), "crouching");
			checkVec(new Vec3(offset.x, offset.y * 0.6 / 1.8, offset.z), scale(offset, 0.6F, 0.6F),
					"swimming, fall flying or spin attacking");
			checkVec(new Vec3(offset.x * 0.2 / 0.6, offset.y * 0.2 / 1.8, offset.z * 0.2 / 0.6), scale(offset, 0.2F, 0.2F),
					"sleeping or dying");
		}
	}

	// the arms-only drop, 0.85 * (pose height - 1.8), is in the y before the scale, as getAbsoluteVec added it
	private static void checkThe116Values() {
		checkClose(0.2 * 0.6 / 1.8, scale(new Vec3(-0.75, 0.2, -0.75), 0.6F, 0.6F).y, "1.16: a swimming user's idle Stand's y");
		checkClose(-0.2125, scale(new Vec3(0, (1.5 - 1.8) * 0.85, 0.15), 0.6F, 1.5F).y,
				"1.16: an arms-only Stand's y with a crouching user that does not sneak");
		checkClose(-0.34, scale(new Vec3(0, (0.6 - 1.8) * 0.85, 0.15), 0.6F, 0.6F).y,
				"1.16: an arms-only Stand's y with a swimming user");
		checkClose(0.2 * 1.5 / 1.8, scale(new Vec3(0, 0.2, 0.3), 0.6F, 1.5F).y, "1.16: a guarding Stand's y with a crouching user");
		Vec3 lying = scale(new Vec3(-0.75, 0.2, -0.75), 0.2F, 0.2F);
		checkVec(new Vec3(-0.25, 0.2 / 9, -0.25), lying, "1.16: a sleeping or dying user's idle Stand, a third as far across");
	}

	private static Vec3[] offsets() {
		return new Vec3[] { new Vec3(0.75, 0.2, -0.75), new Vec3(-0.75, 0.2, -0.75), new Vec3(0, 0, 0.15),
				new Vec3(-1, 0.2, 1.5), new Vec3(0, -0.3, 0.5), Vec3.ZERO };
	}

	private static Vec3 scale(Vec3 offset, float width, float height) {
		return StandOffsetFromUser.scaleOffsetForUser(offset, width, height, PLAYER_WIDTH, PLAYER_HEIGHT);
	}

	private static void checkVec(Vec3 expected, Vec3 actual, String message) {
		checkClose(expected.x, actual.x, message + " (across)");
		checkClose(expected.y, actual.y, message + " (up)");
		checkClose(expected.z, actual.z, message + " (forward)");
	}

	private static void checkClose(double expected, double actual, String message) {
		if (Math.abs(expected - actual) > EPSILON || !Double.isFinite(actual)) {
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
		}
	}
}
