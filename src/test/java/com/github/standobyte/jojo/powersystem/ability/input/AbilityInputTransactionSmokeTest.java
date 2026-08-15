package com.github.standobyte.jojo.powersystem.ability.input;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import com.github.standobyte.jojo.powersystem.ability.input.AbilityInput.ReleaseResult;

public final class AbilityInputTransactionSmokeTest {
	private AbilityInputTransactionSmokeTest() {}

	public static void run() {
		testReleaseSyncSurvivesCallbackFailure();
		testReleaseCombinesCallbackAndSyncFailures();
		testIdempotentReleaseStillRunsAuthoritativeSync();
		testGenerationScopedRelease();
		testPressAndAimRollbackContracts();
	}

	private static void testGenerationScopedRelease() {
		HeldInputEntry restoredA = new HeldInputEntry(
				(short) 4, 11L, null, user -> {});
		HeldInputEntry newerC = new HeldInputEntry(
				(short) 4, 13L, null, user -> {});
		check(AbilityInput.classifyRelease(restoredA, 12L, 12L)
				== ReleaseResult.STALE,
				"rollback release B must not clear restored hold A");
		check(AbilityInput.classifyRelease(newerC, 13L, 12L)
				== ReleaseResult.STALE,
				"rollback release B must not clear newer hold C");
		check(AbilityInput.classifyRelease(restoredA, 12L, 11L)
				== ReleaseResult.RELEASED,
				"an exact generation release must clear its held input");
		check(AbilityInput.classifyRelease(null, 12L, 12L)
				== ReleaseResult.IDEMPOTENT,
				"a repeated exact release must remain idempotent");
	}

	private static void testReleaseSyncSurvivesCallbackFailure() {
		AtomicInteger callbackCount = new AtomicInteger();
		AtomicInteger syncCount = new AtomicInteger();
		HeldInputEntry held = new HeldInputEntry(
				(short) 7,
				null,
				user -> {
					callbackCount.incrementAndGet();
					throw new IllegalStateException("addon_release_fault");
				});
		IllegalStateException failure = expectThrows(
				IllegalStateException.class,
				() -> AbilityInput.releaseHeldInput(
						held, null, syncCount::incrementAndGet),
				"addon release failure must be surfaced after cleanup");
		check("addon_release_fault".equals(failure.getMessage())
				&& callbackCount.get() == 1
				&& syncCount.get() == 1,
				"release callback and downstream sync must each execute exactly once");
	}

	private static void testReleaseCombinesCallbackAndSyncFailures() {
		AtomicInteger syncCount = new AtomicInteger();
		HeldInputEntry held = new HeldInputEntry(
				(short) 8,
				null,
				user -> {
					throw new IllegalStateException("callback");
				});
		IllegalStateException failure = expectThrows(
				IllegalStateException.class,
				() -> AbilityInput.releaseHeldInput(held, null, () -> {
					syncCount.incrementAndGet();
					throw new IllegalArgumentException("sync");
				}),
				"release must preserve both failures");
		check(syncCount.get() == 1
				&& failure.getSuppressed().length == 1
				&& failure.getSuppressed()[0] instanceof IllegalArgumentException,
				"sync failure must be suppressed onto the callback failure once");
	}

	private static void testIdempotentReleaseStillRunsAuthoritativeSync() {
		AtomicInteger syncCount = new AtomicInteger();
		boolean removed = AbilityInput.releaseHeldInput(
				null, null, syncCount::incrementAndGet);
		check(!removed && syncCount.get() == 1,
				"authoritative repeated release must remain idempotent and observable");
	}

	private static void testPressAndAimRollbackContracts() {
		String abilityInput = read(
				"src/main/java/com/github/standobyte/jojo/powersystem/ability/input/AbilityInput.java");
		int heldCommit = abilityInput.indexOf(
				"inputState.heldKeys.put(keyId, heldInput)");
		int cooldownCommit = abilityInput.indexOf(
				"ability.setCooldownOnUse(power)", heldCommit);
		int replayCommit = abilityInput.indexOf(
				"TrAbilityUsePacket.keyPress(", cooldownCommit);
		int rollback = abilityInput.indexOf(
				"transaction.rollback(error)", replayCommit);
		check(heldCommit >= 0 && cooldownCommit > heldCommit
				&& replayCommit > cooldownCommit && rollback > replayCommit,
				"press side effects must share one ordered rollback boundary");
		check(abilityInput.contains("inputBuffer.buffered = bufferedBefore")
				&& abilityInput.contains("setAbilityCooldown(")
				&& abilityInput.contains("rollbackFailedInputAction(")
				&& abilityInput.contains("captureTransactionSnapshot()")
				&& abilityInput.contains("current.generation == inputGeneration")
				&& abilityInput.contains("inputGeneration)))")
				&& abilityInput.contains("sendToPlayersTrackingEntityAndSelf("),
				"press rollback must restore lifecycle state without releasing another generation");
		String actionComponent = read(
				"src/main/java/com/github/standobyte/jojo/powersystem/entityaction/LivingComponentAction.java");
		String actionInstance = read(
				"src/main/java/com/github/standobyte/jojo/powersystem/entityaction/EntityActionInstance.java");
		check(actionComponent.contains("lifecycleRemovalAttempts")
				&& actionComponent.contains("removalAttempted")
				&& actionComponent.contains("restoreInputLifecycle(")
				&& actionInstance.contains("_onActionStarted(failedReplacement)"),
				"rollback must restart a prior action whose teardown began before failure");

		String handler = read(
				"src/main/java/com/github/standobyte/jojo/network/c2s/ClAbilityInputPacket.java");
		int movesetAuth = handler.indexOf(
				"power.getAbility(requestedActiveAbility.name())");
		int targetResolve = handler.indexOf(
				"AimTargetTransaction.resolve(payload, player)", movesetAuth);
		int targetApply = handler.indexOf("targets.apply()", targetResolve);
		int press = handler.indexOf("AbilityInput.keyPress(", targetApply);
		int targetRollback = handler.indexOf("targets.rollback()", press);
		check(movesetAuth >= 0 && targetResolve > movesetAuth
				&& targetApply > targetResolve && press > targetApply
				&& targetRollback > press,
				"aim targets must follow moveset authorization and roll back on failed press");
	}

	private static String read(String path) {
		try {
			return Files.readString(Path.of(path));
		}
		catch (IOException error) {
			throw new AssertionError("failed to read " + path, error);
		}
	}

	private static <T extends Throwable> T expectThrows(
			Class<T> expected,
			Runnable action,
			String message) {
		try {
			action.run();
		}
		catch (Throwable actual) {
			if (expected.isInstance(actual)) {
				return expected.cast(actual);
			}
			throw new AssertionError(message, actual);
		}
		throw new AssertionError(message);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
