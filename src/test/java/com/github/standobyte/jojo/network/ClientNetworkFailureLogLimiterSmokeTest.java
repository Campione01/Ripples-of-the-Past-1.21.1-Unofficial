package com.github.standobyte.jojo.network;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.github.standobyte.jojo.network.ClientNetworkFailureLogLimiter.Decision;

public final class ClientNetworkFailureLogLimiterSmokeTest {
	private ClientNetworkFailureLogLimiterSmokeTest() {}

	public static void run() {
		ClientNetworkFailureLogLimiter limiter =
				new ClientNetworkFailureLogLimiter(2, 100L, 50L, 2, 500L);
		Decision firstReplay = limiter.acquire(
				"ability_replay_resolution", "17/unresolved",
				IllegalStateException.class, 0L);
		Decision repeatedReplay = limiter.acquire(
				"ability_replay_resolution", "17/unresolved",
				IllegalStateException.class, 1L);
		Decision independentActionSync = limiter.acquire(
				"entity_action_sync", "17",
				IllegalStateException.class, 2L);
		Decision overCapacity = limiter.acquire(
				"ability_replay_execution", "18/jojo:test",
				IllegalArgumentException.class, 3L);
		check(firstReplay.logStackTrace(),
				"the first client replay failure must be logged");
		check(!repeatedReplay.logStackTrace(),
				"repeated client replay failures must be suppressed");
		check(independentActionSync.logStackTrace(),
				"client replay and action sync must have independent classifications");
		check(!overCapacity.logStackTrace()
				&& overCapacity.classificationCapacityReached()
				&& limiter.classificationCount() == 2,
				"client failure classifications must fail closed at fixed capacity");

		Decision replayAfterWindow = limiter.acquire(
				"ability_replay_resolution", "17/unresolved",
				IllegalStateException.class, 101L);
		check(replayAfterWindow.logStackTrace()
				&& replayAfterWindow.suppressedCount() == 1L,
				"client replay logging must recover and summarize suppression");

		ClientNetworkFailureLogLimiter globalLimiter =
				new ClientNetworkFailureLogLimiter(8, 100L, 50L, 1, 500L);
		check(globalLimiter.acquire(
				"ability_replay_execution", "17/jojo:test",
				IllegalStateException.class, 0L).logStackTrace(),
				"the first client failure must consume the global budget");
		check(!globalLimiter.acquire(
				"entity_action_sync", "17",
				IllegalArgumentException.class, 1L).logStackTrace(),
				"the client global budget must suppress cross-path bursts");
		Decision actionAfterGlobalWindow = globalLimiter.acquire(
				"entity_action_sync", "17",
				IllegalArgumentException.class, 51L);
		check(actionAfterGlobalWindow.logStackTrace()
				&& actionAfterGlobalWindow.suppressedCount() == 1L,
				"action sync logging must recover after the global window");

		ClientNetworkFailureLogLimiter expiringLimiter =
				new ClientNetworkFailureLogLimiter(1, 100L, 50L, 2, 200L);
		expiringLimiter.acquire(
				"ability_replay_execution", "17/jojo:test",
				IllegalStateException.class, 0L);
		Decision afterExpiry = expiringLimiter.acquire(
				"entity_action_sync", "17",
				IllegalStateException.class, 201L);
		check(afterExpiry.logStackTrace()
				&& expiringLimiter.classificationCount() == 1,
				"idle client classifications must expire before capacity is checked");

		checkLoggingOnlyContracts();
	}

	private static void checkLoggingOnlyContracts() {
		Path sourceRoot = Path.of(System.getProperty("user.dir")).resolve(
				"src/main/java/com/github/standobyte/jojo");
		String replayHandler = read(sourceRoot.resolve(
				"network/s2c/TrAbilityUsePacket.java"));
		int replayExecution = replayHandler.indexOf("AbilityInput.keyPress(");
		int replayApplied = replayHandler.indexOf(
				"Stage.CLIENT_REPLAY_APPLIED", replayExecution);
		int replayCatch = replayHandler.indexOf(
				"catch (RuntimeException error)", replayApplied);
		int replayRejected = replayHandler.indexOf(
				"Stage.CLIENT_REPLAY_REJECTED", replayCatch);
		int replayLimiter = replayHandler.indexOf(
				"ClientNetworkFailureLogLimiter.acquire(", replayRejected);
		check(replayExecution >= 0
				&& replayApplied > replayExecution
				&& replayCatch > replayApplied
				&& replayRejected > replayCatch
				&& replayLimiter > replayRejected,
				"replay limiting must remain on the failed logging path only");
		check(replayHandler.contains(
				"payload, resolvedAbility, \"execution\", true, error)")
				&& replayHandler.contains(
						"logReplayFailure(payload, null, \"resolution\", false, e)"),
				"both replay failure paths must use the bounded logger");
		check(!replayHandler.contains(".disconnect("),
				"client replay failures must not disconnect the client");

		String actionQueue = read(sourceRoot.resolve(
				"powersystem/entityaction/netcode/ClientEntityActionSyncQueue.java"));
		int setAction = actionQueue.indexOf(
				"setActionFromNetwork(action, generation)");
		int actionApplied = actionQueue.indexOf(
				"Stage.CLIENT_ACTION_SYNC_APPLIED", setAction);
		int actionCatch = actionQueue.indexOf(
				"catch (RuntimeException error)", actionApplied);
		int rollback = actionQueue.indexOf(
				"rollbackFailedInputAction(snapshot)", actionCatch);
		int actionRejected = actionQueue.indexOf(
				"Stage.CLIENT_ACTION_SYNC_REJECTED", rollback);
		int actionLimiter = actionQueue.indexOf(
				"ClientNetworkFailureLogLimiter.acquire(", actionRejected);
		check(setAction >= 0
				&& actionApplied > setAction
				&& actionCatch > actionApplied
				&& rollback > actionCatch
				&& actionRejected > rollback
				&& actionLimiter > actionRejected,
				"action sync limiting must not gate apply or cleanup behavior");
		check(actionQueue.contains("applyPendingActions()")
				&& actionQueue.contains("ApplyResult result = applyAction(")
				&& actionQueue.contains("result == ApplyResult.RETRY")
				&& actionQueue.contains("iterator.remove()")
				&& actionQueue.contains(
						"applyPendingFor(identity, generation)"),
				"queued action recovery must remain connected to normal sync");
	}

	private static String read(Path path) {
		try {
			return Files.readString(path);
		}
		catch (IOException exception) {
			throw new AssertionError("failed to read " + path, exception);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
