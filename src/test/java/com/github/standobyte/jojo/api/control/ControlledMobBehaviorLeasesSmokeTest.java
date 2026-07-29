package com.github.standobyte.jojo.api.control;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import com.github.standobyte.jojo.api.control.ControlledMobBehaviorLeases.AcquireResult;
import com.github.standobyte.jojo.api.control.ControlledMobBehaviorLeases.AcquireStatus;
import com.github.standobyte.jojo.api.control.ControlledMobBehaviorLeases.Lease;
import com.github.standobyte.jojo.api.control.ControlledMobBehaviorLeases.Owner;
import com.github.standobyte.jojo.api.control.ControlledMobBehaviorLeases.ReleaseStatus;
import com.github.standobyte.jojo.api.control.ControlledMobBehaviorLeases.SubjectAccess;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;

public final class ControlledMobBehaviorLeasesSmokeTest {
	private ControlledMobBehaviorLeasesSmokeTest() {}

	public static void run() {
		ControlledMobBehaviorLeases.resetForTests();
		Owner first = ControlledMobBehaviorLeases.register(id("first"));
		Owner second = ControlledMobBehaviorLeases.register(id("second"));
		expectIllegalState(() ->
				ControlledMobBehaviorLeases.register(id("first")));

		Object scope = new Object();
		FakeSubject subject = new FakeSubject(
				scope, UUID.randomUUID(), true);
		subject.goalSelector.add("other_original_goal");
		subject.targetSelector.add("other_original_target");

		UUID aggressiveId = UUID.randomUUID();
		AcquireResult aggressive = first.acquireForTests(
				subject,
				MobBehaviorMode.AGGRESSIVE_NEAREST_8,
				aggressiveId);
		check(aggressive.status() == AcquireStatus.ACQUIRED
						&& aggressive.lease() != null
						&& subject.hasCoreGoals(),
				"aggressive behavior was not installed");
		Lease firstAggressive = aggressive.lease();
		AcquireResult duplicate = first.acquireForTests(
				subject,
				MobBehaviorMode.AGGRESSIVE_NEAREST_8,
				aggressiveId);
		check(duplicate.status() == AcquireStatus.ALREADY_ACTIVE
						&& duplicate.lease() == firstAggressive,
				"identical behavior reapplication was not idempotent");
		AcquireResult conflict = first.acquireForTests(
				subject,
				MobBehaviorMode.PEACEFUL,
				aggressiveId);
		check(conflict.status() == AcquireStatus.CONFLICT
						&& conflict.lease() == null,
				"mismatched behavior lease ID was not rejected");

		Lease secondAggressive = second.acquireForTests(
				subject,
				MobBehaviorMode.AGGRESSIVE_NEAREST_8,
				UUID.randomUUID()).lease();
		subject.goalSelector.add("other_concurrent_goal");
		subject.targetSelector.remove("other_original_target");
		subject.targetSelector.add("other_concurrent_target");

		Lease peaceful = first.acquireForTests(
				subject,
				MobBehaviorMode.PEACEFUL,
				UUID.randomUUID()).lease();
		check(ControlledMobBehaviorLeases.isPeacefulForTests(
						scope, subject.id)
						&& !subject.hasCoreGoals()
						&& subject.clearCalls > 0,
				"peaceful lease did not take precedence");
		int clears = subject.clearCalls;
		ControlledMobBehaviorLeases.tickForTests(scope, subject.id);
		check(subject.clearCalls == clears + 1,
				"peaceful state was not enforced on tick");

		check(second.release(peaceful) == ReleaseStatus.WRONG_OWNER
						&& peaceful.isActive(),
				"foreign owner released a behavior token");
		check(first.release(peaceful) == ReleaseStatus.RELEASED
						&& first.release(peaceful)
								== ReleaseStatus.ALREADY_RELEASED
						&& subject.hasCoreGoals(),
				"releasing peaceful mode did not restore active aggressive mode");
		check(first.release(firstAggressive) == ReleaseStatus.RELEASED
						&& subject.hasCoreGoals(),
				"one concurrent aggressive release removed shared goals");
		check(second.release(secondAggressive) == ReleaseStatus.RELEASED
						&& !subject.hasCoreGoals(),
				"last aggressive release retained core goals");
		check(subject.goalSelector.equals(Set.of(
						"other_original_goal",
						"other_concurrent_goal"))
						&& subject.targetSelector.equals(Set.of(
								"other_concurrent_target")),
				"release overwrote unrelated selector mutations");

		FakeSubject ambient = new FakeSubject(
				scope, UUID.randomUUID(), false);
		check(first.acquireForTests(
						ambient,
						MobBehaviorMode.AGGRESSIVE_NEAREST_8,
						UUID.randomUUID()).status()
						== AcquireStatus.UNSUPPORTED_SUBJECT,
				"ambient aggressive behavior was accepted");
		Lease ambientPeaceful = first.acquireForTests(
				ambient,
				MobBehaviorMode.PEACEFUL,
				UUID.randomUUID()).lease();
		check(ambientPeaceful != null
						&& ambient.clearCalls > 0,
				"ambient peaceful behavior was rejected");
		first.release(ambientPeaceful);

		assertUnloadAndReload(first, scope);
		verifyRuntimeCallPaths();
		ControlledMobBehaviorLeases.resetForTests();
	}

	private static void assertUnloadAndReload(
			Owner owner, Object scope) {
		UUID id = UUID.randomUUID();
		FakeSubject loaded = new FakeSubject(scope, id, true);
		Lease oldLease = owner.acquireForTests(
				loaded,
				MobBehaviorMode.AGGRESSIVE_NEAREST_8,
				UUID.randomUUID()).lease();
		loaded.active = false;
		check(ControlledMobBehaviorLeases
						.activeLeaseCountForTests() == 0
						&& !oldLease.isActive()
						&& !loaded.hasCoreGoals(),
				"unloaded mob retained goals or token");

		FakeSubject reloaded = new FakeSubject(scope, id, true);
		Lease reloadedLease = owner.acquireForTests(
				reloaded,
				MobBehaviorMode.PEACEFUL,
				UUID.randomUUID()).lease();
		check(reloadedLease != null
						&& ControlledMobBehaviorLeases
								.isPeacefulForTests(scope, id),
				"reloaded mob could not reacquire persisted module mode");
		ControlledMobBehaviorLeases.releaseSubjectForTests(scope, id);
		check(!reloadedLease.isActive(),
				"mob removal did not auto-release behavior token");
	}

	private static void verifyRuntimeCallPaths() {
		Path root = Path.of(System.getProperty("user.dir"));
		String source = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/api/control/"
				+ "ControlledMobBehaviorLeases.java"));
		for (String token : new String[] {
				"AGGRESSIVE_GOAL_PRIORITY = 2",
				"AGGRESSIVE_SPEED = 1.0D",
				"AGGRESSIVE_RANGE = 8.0D",
				"AGGRESSIVE_LONG_MEMORY = false",
				"instanceof AmbientCreature",
				"goalSelector.addGoal",
				"goalSelector.removeGoal",
				"targetSelector.addGoal",
				"targetSelector.removeGoal",
				"LivingChangeTargetEvent",
				"LivingIncomingDamageEvent",
				"setLastHurtByMob(null)",
				"setLastHurtMob(null)",
				"EntityLeaveLevelEvent",
				"LivingDeathEvent"
		}) {
			check(source.contains(token),
					"behavior runtime path missing: " + token);
		}
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
				"duplicate behavior lease owner was accepted");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static final class FakeSubject implements SubjectAccess {
		private static final String CORE_ATTACK = "core_attack";
		private static final String CORE_TARGET = "core_target";

		private final Object scope;
		private final UUID id;
		private final boolean aggressiveSupported;
		private final Set<String> goalSelector = new LinkedHashSet<>();
		private final Set<String> targetSelector = new LinkedHashSet<>();
		private boolean active = true;
		private int clearCalls;

		private FakeSubject(
				Object scope,
				UUID id,
				boolean aggressiveSupported) {
			this.scope = scope;
			this.id = id;
			this.aggressiveSupported = aggressiveSupported;
		}

		@Override
		public Object scopeKey() {
			return scope;
		}

		@Override
		public UUID id() {
			return id;
		}

		@Override
		public boolean isActive() {
			return active;
		}

		@Override
		public boolean supportsAggressive() {
			return aggressiveSupported;
		}

		@Override
		public boolean sameSubject(SubjectAccess other) {
			return this == other;
		}

		@Override
		public boolean matches(Mob mob) {
			return false;
		}

		@Override
		public void installAggressiveGoals() {
			goalSelector.add(CORE_ATTACK);
			targetSelector.add(CORE_TARGET);
		}

		@Override
		public void removeAggressiveGoals() {
			goalSelector.remove(CORE_ATTACK);
			targetSelector.remove(CORE_TARGET);
		}

		@Override
		public void clearCombatState() {
			clearCalls++;
		}

		private boolean hasCoreGoals() {
			return goalSelector.contains(CORE_ATTACK)
					&& targetSelector.contains(CORE_TARGET);
		}
	}
}
