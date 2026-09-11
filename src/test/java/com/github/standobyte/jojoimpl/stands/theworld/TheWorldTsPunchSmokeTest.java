package com.github.standobyte.jojoimpl.stands.theworld;

public final class TheWorldTsPunchSmokeTest {
	private static final double EPSILON = 1.0E-9;

	private TheWorldTsPunchSmokeTest() {}

	public static void main(String[] args) {
		checkRatio(15, 10, 20, 0.25, "partial budget overshot its affordable distance");
		checkRatio(10, 10, 20, 0, "windup-only budget moved the Stand");
		checkRatio(5, 10, 20, 0, "insufficient windup budget moved the Stand");
		checkRatio(0, 10, 20, 0, "zero budget moved the Stand");
		checkRatio(30, 10, 20, 1, "full budget did not reach its destination");
		checkRatio(100, 10, 20, 1, "excess budget overshot its destination");
		checkRatio(31, 30, 20, 0.05, "previous-action windup was not reserved");
		checkRatio(30, 10, 0, 1, "zero-distance destination divided by zero");
		checkRatio(5, 10, 0, 0, "zero distance bypassed the windup budget");
		checkRatio(30, 10, Double.NaN, 0, "NaN distance produced an invalid movement ratio");
		checkRatio(30, 10, Double.POSITIVE_INFINITY, 0, "infinite distance allowed movement");
		checkRatio(30, 10, Double.NEGATIVE_INFINITY, 0, "negative infinite distance allowed movement");
		checkRatio(30, 10, -1, 0, "negative distance allowed movement");
		checkRatio(Integer.MAX_VALUE, 10, 0.5, 1, "creative budget escaped the upper bound");

		for (int budget = 0; budget <= 100; budget++) {
			double ratio = TheWorldTSPunchAbility.getBlinkDistanceRatio(budget, 10, 20);
			check(Double.isFinite(ratio) && ratio >= 0 && ratio <= 1,
					"movement ratio escaped [0, 1] for budget " + budget);
			check(ratio * 20 <= Math.max(budget - 10, 0) + EPSILON,
					"movement spent windup ticks for budget " + budget);
		}

		checkLanding(100.7095, 101, 101, "taller Stand landed below the target's floor");
		checkLanding(101.4, 101, 101.4, "valid higher landing was lowered");
		checkLanding(118.7, 120, 120, "airborne target lost its feet-height lower bound");
		checkLanding(121, 120, 121, "higher airborne landing was lowered");
		checkLanding(-64.2, -63, -63, "negative-world-height landing ignored the target's feet");

		System.out.println("The World TS Punch focused smoke test passed.");
	}

	private static void checkLanding(double alignedFeetY, double targetFeetY, double expected, String message) {
		double actual = TheWorldTSPunchAbility.getEntityBlinkFeetY(alignedFeetY, targetFeetY);
		check(Math.abs(actual - expected) <= EPSILON, message + ": expected=" + expected + ", actual=" + actual);
	}

	private static void checkRatio(int budget, int windup, double distance, double expected, String message) {
		double actual = TheWorldTSPunchAbility.getBlinkDistanceRatio(budget, windup, distance);
		check(Math.abs(actual - expected) <= EPSILON, message + ": expected=" + expected + ", actual=" + actual);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
