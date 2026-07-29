package com.github.standobyte.jojo.api.timestop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.ResourceLocation;

public final class TimeStopEntityMovementAuthorizersSmokeTest {
	private TimeStopEntityMovementAuthorizersSmokeTest() {}

	public static void run() {
		TimeStopEntityMovementAuthorizers.resetForTests();
		check(!TimeStopEntityMovementAuthorizers
						.canMoveInStoppedTime(null),
				"movement authorizers must default to false");

		List<String> calls = new ArrayList<>();
		ResourceLocation first = id("first");
		ResourceLocation failing = id("failing");
		ResourceLocation allowing = id("allowing");
		ResourceLocation skipped = id("skipped");
		TimeStopEntityMovementAuthorizers.register(
				first,
				entity -> {
					calls.add("first");
					return false;
				});
		TimeStopEntityMovementAuthorizers.register(
				failing,
				entity -> {
					calls.add("failing");
					throw new IllegalStateException(
							"isolated test failure");
				});
		TimeStopEntityMovementAuthorizers.register(
				allowing,
				entity -> {
					calls.add("allowing");
					return true;
				});
		TimeStopEntityMovementAuthorizers.register(
				skipped,
				entity -> {
					calls.add("skipped");
					return true;
				});

		check(TimeStopEntityMovementAuthorizers.evaluate(null),
				"registered movement authorization was ignored");
		check(calls.equals(List.of(
				"first", "failing", "allowing")),
				"movement authorizers lost ordered any-match semantics");
		check(TimeStopEntityMovementAuthorizers.registeredOwners()
						.equals(List.of(
								first, failing, allowing, skipped)),
				"movement authorizer owner order changed");

		expectIllegalState(() ->
				TimeStopEntityMovementAuthorizers.register(
						first, entity -> true));
		TimeStopEntityMovementAuthorizers.resetForTests();
		check(!TimeStopEntityMovementAuthorizers.evaluate(null),
				"movement authorizers did not fail closed by default");

		verifyRuntimeCallPaths();
	}

	private static void verifyRuntimeCallPaths() {
		Path root = Path.of(System.getProperty("user.dir"));
		String state = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/"
				+ "subsystems/timestop/TimeStopState.java"));
		String clientDecision = between(
				state,
				"private static boolean "
				+ "canEntityMoveInStoppedTimeClient",
				"private void applyFrozenPosition");
		assertAuthorizationBeforeStandRecursion(
				clientDecision,
				"isTimeStoppedClientEntity(entity)",
				"client");

		String serverDecision = between(
				state,
				"private boolean canEntityMoveInStoppedTime",
				"private static boolean hasTimeStopAbility");
		assertAuthorizationBeforeStandRecursion(
				serverDecision,
				"isTimeStopped(entity)",
				"server");

		String shouldFreeze = between(
				state,
				"public boolean shouldFreeze(Entity entity)",
				"private static boolean isTimeStoppedClientEntity");
		check(shouldFreeze.contains(
				"isTimeStopped(entity) "
				+ "&& !canEntityMoveInStoppedTime(entity)")
				&& shouldFreeze.contains(
						"!canEntityMoveInStoppedTimeClient(entity)"),
				"final server/client freeze decisions bypass movement policy");
		check(state.contains(
				"public boolean interruptTickEarly(Entity entity)")
				&& state.contains(
						"if (!shouldInterruptEarly(entity))")
				&& state.contains(
						"return shouldFreeze(entity)"),
				"server early interruption no longer depends on shouldFreeze");

		String serverMixin = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/mixin/"
				+ "timestop/ServerLevelTimeStopTickMixin.java"));
		check(serverMixin.contains(
				"state.interruptTickEarly(entity)"),
				"server entity tick mixin bypasses movement policy");

		String clientMixin = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/mixin/"
				+ "client/timestop/ClientLevelTimeStopTickMixin.java"));
		check(clientMixin.contains(
				"TimeStopState.shouldFreezeClientEntity(entity)"),
				"client entity tick mixin bypasses movement policy");
	}

	private static void assertAuthorizationBeforeStandRecursion(
			String decision,
			String stoppedCheck,
			String side) {
		int stopped = decision.indexOf(stoppedCheck);
		int authorization = decision.indexOf(
				"TimeStopEntityMovementAuthorizers");
		int standRecursion = decision.indexOf(
				"entity instanceof StandEntity");
		check(stopped >= 0
				&& stopped < authorization
				&& authorization < standRecursion,
				side + " movement authorization ordering changed");
	}

	private static String between(
			String source, String startToken, String endToken) {
		int start = source.indexOf(startToken);
		int end = source.indexOf(endToken, start);
		if (start < 0 || end < 0 || end <= start) {
			throw new AssertionError(
					"failed to locate source contract between "
							+ startToken + " and " + endToken);
		}
		return source.substring(start, end);
	}

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(
				"rotp_test", path);
	}

	private static String read(Path path) {
		try {
			return Files.readString(path);
		}
		catch (IOException error) {
			throw new AssertionError("failed to read " + path, error);
		}
	}

	private static void expectIllegalState(Runnable action) {
		try {
			action.run();
		}
		catch (IllegalStateException expected) {
			return;
		}
		throw new AssertionError(
				"duplicate movement authorizer was accepted");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
