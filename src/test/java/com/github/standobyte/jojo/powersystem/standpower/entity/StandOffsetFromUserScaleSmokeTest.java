package com.github.standobyte.jojo.powersystem.standpower.entity;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.phys.Vec3;

public final class StandOffsetFromUserScaleSmokeTest {
	private static final float DEFAULT_WIDTH = 0.6F;
	private static final double EPSILON = 1.0E-6;

	private StandOffsetFromUserScaleSmokeTest() {}

	public static void main(String[] args) {
		checkScale(1, 1, 1);
		checkScale(1, 1, 0.5F);
		checkScale(1, 1, 0.15F);
		checkScale(0.5F, 1, 1);
		checkScale(0.5F, 2, 0.15F);
		checkScale(1, 2, 0.15F);
		checkScale(1, 1, 1);

		checkPoseHeights();
		checkClose(1, scaleForWidth(0, DEFAULT_WIDTH),
				"zero physical width must retain the legacy scale");
		checkClose(1, scaleForWidth(Float.NaN, DEFAULT_WIDTH),
				"NaN physical width must not corrupt the position");
		checkClose(1, scaleForWidth(Float.POSITIVE_INFINITY, DEFAULT_WIDTH),
				"infinite physical width must not corrupt the position");
		checkClose(1, scaleForWidth(DEFAULT_WIDTH, 0),
				"zero default width must retain the legacy scale");
		System.out.println("StandOffsetFromUserScaleSmokeTest: PASS");
	}

	private static void checkScale(float ageScale, float attributeScale, float pehkuiScale) {
		float standingWidth = DEFAULT_WIDTH * ageScale * attributeScale * pehkuiScale;
		double expected = ageScale * attributeScale * pehkuiScale;
		for (Vec3 offset : new Vec3[] { new Vec3(0.75, 0.2, -0.75),
				new Vec3(-0.75, 0.2, -0.75), new Vec3(0, 0, 0.15),
				new Vec3(-1, 0.2, 1.5), Vec3.ZERO }) {
			Vec3 actual = StandOffsetFromUser.scaleOffsetForUser(
					offset, standingWidth, DEFAULT_WIDTH, ageScale);
			checkClose(offset.x * expected, actual.x, "horizontal offset scale");
			checkClose(offset.y * expected, actual.y, "vertical offset scale");
			checkClose(offset.z * expected, actual.z, "forward/rear offset scale");
			Vec3 restored = StandOffsetFromUser.scaleOffsetForUser(
					offset, DEFAULT_WIDTH, DEFAULT_WIDTH, 1);
			checkClose(offset.x, restored.x, "restore must not compound the horizontal scale");
			checkClose(offset.y, restored.y, "restore must not compound the vertical scale");
			checkClose(offset.z, restored.z, "restore must not compound the forward/rear scale");
		}
	}

	private static void checkPoseHeights() {
		for (float height : new float[] { 1.8F, 1.5F, 0.6F }) {
			EntityDimensions poseDimensions = EntityDimensions.scalable(DEFAULT_WIDTH, height).scale(0.15F);
			checkClose(0.15, scaleForWidth(poseDimensions.width(), DEFAULT_WIDTH),
					"standing, crouching and swimming heights must not become a scale factor");
		}
	}

	private static double scaleForWidth(float scaledWidth, float defaultWidth) {
		return StandOffsetFromUser.scaleOffsetForUser(
				new Vec3(1, 1, 1), scaledWidth, defaultWidth, 1).x;
	}

	private static void checkClose(double expected, double actual, String message) {
		if (Math.abs(expected - actual) > EPSILON || !Double.isFinite(actual)) {
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
		}
	}
}
