package com.github.standobyte.jojo.client.entityrender.stand;

public final class StandSurfaceDiagnosticSmokeTest {
	private static final String TARGET = "test:stand";

	private StandSurfaceDiagnosticSmokeTest() {}

	public static void main(String[] args) {
		verifyDefaultOff();
		verifyExactTarget();
		verifyRenderGuards();
		verifyNearestSurfaceBlend();
		System.out.println("Stand surface diagnostic smoke test passed");
	}

	private static void verifyDefaultOff() {
		String previous = System.getProperty(StandSurfaceDiagnosticPolicy.TARGET_PROPERTY);
		try {
			System.clearProperty(StandSurfaceDiagnosticPolicy.TARGET_PROPERTY);
			check(StandSurfaceDiagnosticPolicy.configuredTarget().isEmpty(),
					"an unset diagnostic target must default to empty");
			check(!allows(StandSurfaceDiagnosticPolicy.configuredTarget(), TARGET, 0.8F, true, 0),
					"the diagnostic must remain disabled without an explicit target");
			System.setProperty(StandSurfaceDiagnosticPolicy.TARGET_PROPERTY, TARGET);
			check(StandSurfaceDiagnosticPolicy.configuredTarget().equals(TARGET),
					"the explicit system property was not read");
		}
		finally {
			if (previous == null) {
				System.clearProperty(StandSurfaceDiagnosticPolicy.TARGET_PROPERTY);
			}
			else {
				System.setProperty(StandSurfaceDiagnosticPolicy.TARGET_PROPERTY, previous);
			}
		}
	}

	private static void verifyExactTarget() {
		check(allows(TARGET, TARGET, 0.8F, true, 0), "an exact target must opt in");
		for (String target : new String[] { null, "", " ", "test", "stand", "test:*", "test:stand ", "TEST:stand" }) {
			check(!allows(target, TARGET, 0.8F, true, 0), "a non-exact target enabled the diagnostic: " + target);
		}
		check(!allows(TARGET, "other:stand", 0.8F, true, 0), "a different namespace was admitted");
		check(!allows(TARGET, "test:stand_other", 0.8F, true, 0), "an entity prefix was admitted");
		check(!allows(TARGET, null, 0.8F, true, 0), "a missing entity type was admitted");
	}

	private static void verifyRenderGuards() {
		for (float alpha : new float[] { Float.NEGATIVE_INFINITY, -0.1F, 0.0F, 1.0F, 1.1F, Float.POSITIVE_INFINITY, Float.NaN }) {
			check(!allows(TARGET, TARGET, alpha, true, 0), "an unsupported alpha was admitted: " + alpha);
		}
		check(allows(TARGET, TARGET, Float.MIN_VALUE, true, 0), "a positive fractional alpha was rejected");
		check(allows(TARGET, TARGET, Math.nextDown(1.0F), true, 0), "a fractional alpha below one was rejected");
		check(!allows(TARGET, TARGET, 0.8F, false, 0), "an invisible body was admitted");
		for (int exclusions = 1; exclusions < 16; exclusions++) {
			check(!allows(TARGET, TARGET, 0.8F, true, exclusions),
					"mask, classic obstruction, afterimage or barrage exclusion failed: " + exclusions);
		}
	}

	private static boolean allows(String target, String entityType, float alpha, boolean bodyVisible, int exclusions) {
		return StandSurfaceDiagnosticPolicy.useNearestSurface(target, entityType, alpha, bodyVisible,
				(exclusions & 1) != 0, (exclusions & 2) != 0, (exclusions & 4) != 0, (exclusions & 8) != 0);
	}

	private static void verifyNearestSurfaceBlend() {
		// A split front plane can straddle its back plane's QUADS sorting key.
		double alpha = 0.8;
		double frontGray = 0.8;
		double backGray = 0.2;
		double frontOnlyAlpha = alpha;
		double backThenFrontAlpha = alpha + alpha * (1.0 - alpha);
		check(close(frontOnlyAlpha, 0.8) && close(backThenFrontAlpha, 0.96),
				"over blending must expose the one-layer versus two-layer counterexample");
		double frontOnlyColor = frontGray * alpha;
		double backThenFrontColor = frontGray * alpha + backGray * alpha * (1.0 - alpha);
		check(!close(frontOnlyColor, backThenFrontColor), "back-face color contamination was not reproduced");
		double nearestColor = frontGray * alpha + backGray * alpha * 0.0;
		double nearestAlpha = alpha + alpha * 0.0;
		check(close(nearestColor, frontOnlyColor) && close(nearestAlpha, frontOnlyAlpha),
				"SRC_ALPHA/ZERO and ONE/ZERO must replace the previous surface with premultiplied RGBA");
	}

	private static boolean close(double actual, double expected) {
		return Math.abs(actual - expected) < 1.0E-10;
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
