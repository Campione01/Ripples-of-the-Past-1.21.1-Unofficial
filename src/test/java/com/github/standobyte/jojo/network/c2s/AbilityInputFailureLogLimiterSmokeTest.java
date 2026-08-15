package com.github.standobyte.jojo.network.c2s;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import com.github.standobyte.jojo.network.c2s.AbilityInputFailureLogLimiter.Decision;

public final class AbilityInputFailureLogLimiterSmokeTest {
	private AbilityInputFailureLogLimiterSmokeTest() {}

	public static void run() {
		UUID player = UUID.randomUUID();
		AbilityInputFailureLogLimiter limiter =
				new AbilityInputFailureLogLimiter(2, 100L, 50L, 2, 500L);
		Decision first = limiter.acquire(
				player, "jojo:test_a", IllegalStateException.class, 0L);
		Decision repeated = limiter.acquire(
				player, "jojo:test_a", IllegalStateException.class, 1L);
		Decision independent = limiter.acquire(
				player, "jojo:test_b", IllegalStateException.class, 2L);
		Decision overCapacity = limiter.acquire(
				player, "jojo:test_c", IllegalStateException.class, 3L);
		check(first.logStackTrace(),
				"the first classified failure must be logged");
		check(!repeated.logStackTrace(),
				"repeated failures must be suppressed inside the class window");
		check(independent.logStackTrace(),
				"different abilities must have independent class windows");
		check(!overCapacity.logStackTrace()
				&& overCapacity.classificationCapacityReached()
				&& limiter.classificationCount() == 2,
				"new classifications must fail closed at the bounded capacity");

		Decision afterWindow = limiter.acquire(
				player, "jojo:test_a", IllegalStateException.class, 101L);
		check(afterWindow.logStackTrace()
				&& afterWindow.suppressedCount() == 1L,
				"the next permitted log must report suppressed repetitions");

		AbilityInputFailureLogLimiter globalLimiter =
				new AbilityInputFailureLogLimiter(8, 100L, 50L, 1, 500L);
		check(globalLimiter.acquire(
				player, "jojo:test_a", IllegalStateException.class, 0L)
				.logStackTrace(),
				"the first failure must consume the global budget");
		check(!globalLimiter.acquire(
				player, "jojo:test_b", IllegalArgumentException.class, 1L)
				.logStackTrace(),
				"the global budget must suppress a classification burst");
		Decision afterGlobalWindow = globalLimiter.acquire(
				player, "jojo:test_b", IllegalArgumentException.class, 51L);
		check(afterGlobalWindow.logStackTrace()
				&& afterGlobalWindow.suppressedCount() == 1L,
				"a suppressed class must recover after the global window");

		AbilityInputFailureLogLimiter expiringLimiter =
				new AbilityInputFailureLogLimiter(1, 100L, 50L, 2, 200L);
		expiringLimiter.acquire(
				player, "jojo:test_a", IllegalStateException.class, 0L);
		Decision afterExpiry = expiringLimiter.acquire(
				player, "jojo:test_b", IllegalStateException.class, 201L);
		check(afterExpiry.logStackTrace()
				&& expiringLimiter.classificationCount() == 1,
				"idle classifications must expire before capacity is checked");

		AbilityInputFailureLogLimiter unresolvedLimiter =
				new AbilityInputFailureLogLimiter(2, 100L, 50L, 2, 500L);
		Decision firstUnresolved = unresolvedLimiter.acquire(
				player, "unresolved", IllegalArgumentException.class, 0L);
		Decision repeatedUnresolved = unresolvedLimiter.acquire(
				player, "unresolved", IllegalArgumentException.class, 1L);
		check(firstUnresolved.logStackTrace()
				&& !repeatedUnresolved.logStackTrace()
				&& unresolvedLimiter.classificationCount() == 1,
				"repeated nonexistent abilities must share one limited classification");

		checkHandlerContainmentContract();
	}

	private static void checkHandlerContainmentContract() {
		Path handlerPath = Path.of(System.getProperty("user.dir")).resolve(
				"src/main/java/com/github/standobyte/jojo/network/c2s/"
						+ "ClAbilityInputPacket.java");
		String handler;
		try {
			handler = Files.readString(handlerPath);
		}
		catch (IOException exception) {
			throw new AssertionError("failed to read " + handlerPath, exception);
		}
		int keyPress = handler.indexOf("AbilityInput.keyPress(");
		int successReceipt = handler.indexOf(
				"Stage.SERVER_INPUT_APPLIED", keyPress);
		int limiterHelper = handler.indexOf(
				"private static void containInputFailure");
		int limiterUse = handler.indexOf("AbilityInputFailureLogLimiter.");
		check(keyPress >= 0
				&& successReceipt > keyPress
				&& limiterHelper > successReceipt
				&& limiterUse > limiterHelper,
				"failure-log suppression must remain outside the valid input path");

		int baseResolve = handler.indexOf(
				"Ability baseAbility = resolveAbility(");
		int baseMissingGuard = handler.indexOf(
				"if (baseAbility == null)", baseResolve);
		int activeResolve = handler.indexOf(
				"Ability requestedActiveAbility = resolveAbility(", baseResolve);
		check(baseResolve >= 0
				&& baseMissingGuard > baseResolve
				&& activeResolve > baseMissingGuard
				&& keyPress > activeResolve,
				"failed base resolution must stop before active resolution and execution");

		int resolverStart = handler.indexOf(
				"private static Ability resolveAbility(");
		int resolverEnd = handler.indexOf(
				"private static AbilityConditionCheck resolveServerInputAbility(",
				resolverStart);
		check(resolverStart >= 0 && resolverEnd > resolverStart,
				"ability resolver source contract must remain discoverable");
		String resolver = handler.substring(resolverStart, resolverEnd);
		check(resolver.contains("containInputFailure(")
				&& resolver.contains("abilityRole + \"_resolution\"")
				&& !resolver.contains("getLogger()"),
				"resolution failures must use shared diagnostics and limited logging");
		check(!handler.contains(".disconnect("),
				"contained input failures must not disconnect the player");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
