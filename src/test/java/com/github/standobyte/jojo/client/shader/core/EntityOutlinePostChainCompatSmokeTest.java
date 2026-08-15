package com.github.standobyte.jojo.client.shader.core;

public final class EntityOutlinePostChainCompatSmokeTest {
	private EntityOutlinePostChainCompatSmokeTest() {}

	public static void run() {
		Object first = new Object();
		Object second = new Object();
		Object firstTarget = new Object();
		Object secondTarget = new Object();
		EntityOutlinePostChainCompat.State state =
				new EntityOutlinePostChainCompat.State(
						first, firstTarget, 960, 540);

		check(!EntityOutlinePostChainCompat.requiresResize(
				state, first, firstTarget, 960, 540),
				"stable outline chain was resized again");
		check(EntityOutlinePostChainCompat.requiresResize(
				state, first, firstTarget, 320, 180),
				"scaled outline extent was not detected");
		check(EntityOutlinePostChainCompat.requiresResize(
				state, first, secondTarget, 960, 540),
				"replacement screen target was not detected");
		check(EntityOutlinePostChainCompat.requiresResize(
				state, second, firstTarget, 960, 540),
				"replacement outline chain was not detected");
		check(EntityOutlinePostChainCompat.requiresResize(
				null, first, firstTarget, 960, 540),
				"uninitialized outline chain was not normalized");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
