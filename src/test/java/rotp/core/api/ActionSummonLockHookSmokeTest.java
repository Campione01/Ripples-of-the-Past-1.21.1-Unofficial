package rotp.core.api;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import rotp.core.impl.stands.theworld.TimeStopAbility;
import rotp.core.powersystem.entityaction.EntityActionInstance;

/**
 * FEATURE_ACTION_SUMMON_LOCK_HOOK_V1: EntityActionInstance#isHeldBackBySummonLock, a public overridable boolean that is
 * true by default (1.16's StandEntityAction, held back by its Stand's summon lock) and false for the core's time stop
 * (1.16's TimeStop, a StandAction), and EntityActionInstance#getTicksHeldInSummonLock, the ticks a held action counted
 * from its press while the lock held it back. StandActionSummonLockGameTests and StandSummonLockTickGameTests check them.
 */
final class ActionSummonLockHookSmokeTest {
	private ActionSummonLockHookSmokeTest() {}

	static void run() {
		check(RotpAddonApi.supportsFeature(RotpAddonApi.FEATURE_ACTION_SUMMON_LOCK_HOOK_V1)
				&& "action_summon_lock_hook_v1".equals(RotpAddonApi.FEATURE_ACTION_SUMMON_LOCK_HOOK_V1),
				"Action summon lock hook feature missing");
		try {
			Method hook = EntityActionInstance.class.getDeclaredMethod("isHeldBackBySummonLock");
			int modifiers = hook.getModifiers();
			check(Modifier.isPublic(modifiers) && !Modifier.isFinal(modifiers) && !Modifier.isStatic(modifiers)
					&& hook.getReturnType() == boolean.class,
					"EntityActionInstance#isHeldBackBySummonLock() must be a public overridable boolean");
			check(TimeStopAbility.TimeStopAction.class.getDeclaredMethod("isHeldBackBySummonLock")
					.getReturnType() == boolean.class, "the core's time stop must answer the summon lock hook itself");
			Method held = EntityActionInstance.class.getDeclaredMethod("getTicksHeldInSummonLock");
			check(Modifier.isPublic(held.getModifiers()) && !Modifier.isStatic(held.getModifiers())
					&& held.getReturnType() == int.class,
					"EntityActionInstance#getTicksHeldInSummonLock() must be a public int getter");
		}
		catch (ReflectiveOperationException error) {
			throw new AssertionError("Action summon lock hook API surface missing", error);
		}
		check(new EntityActionInstance(null).isHeldBackBySummonLock(),
				"an action must be held back by its Stand's summon lock by default, as 1.16's Stand task was");
		check(!new TimeStopAbility.TimeStopAction(null).isHeldBackBySummonLock(),
				"the time stop, 1.16's StandAction, must not be held back by its Stand's summon lock");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
