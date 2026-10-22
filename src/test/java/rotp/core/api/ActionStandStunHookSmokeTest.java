package rotp.core.api;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import rotp.core.impl.stands.theworld.TimeStopAbility;
import rotp.core.powersystem.entityaction.EntityActionInstance;

/**
 * FEATURE_ACTION_STAND_STUN_HOOK_V1: EntityActionInstance#isHeldBackByStandStun, a public overridable boolean that answers
 * as isHeldBackBySummonLock by default: true for 1.16's StandEntityAction, the Stand's task its stun held back, and false
 * for a port of a 1.16 StandAction that returns false there (the core's time stop). StandActionStunGameTests checks it.
 */
final class ActionStandStunHookSmokeTest {
	private ActionStandStunHookSmokeTest() {}

	static void run() {
		check(RotpAddonApi.supportsFeature(RotpAddonApi.FEATURE_ACTION_STAND_STUN_HOOK_V1)
				&& "action_stand_stun_hook_v1".equals(RotpAddonApi.FEATURE_ACTION_STAND_STUN_HOOK_V1),
				"Action Stand stun hook feature missing");
		try {
			Method hook = EntityActionInstance.class.getDeclaredMethod("isHeldBackByStandStun");
			int modifiers = hook.getModifiers();
			check(Modifier.isPublic(modifiers) && !Modifier.isFinal(modifiers) && !Modifier.isStatic(modifiers)
					&& hook.getReturnType() == boolean.class,
					"EntityActionInstance#isHeldBackByStandStun() must be a public overridable boolean");
		}
		catch (ReflectiveOperationException error) {
			throw new AssertionError("Action Stand stun hook API surface missing", error);
		}
		check(new EntityActionInstance(null).isHeldBackByStandStun(),
				"an action must be held back by its Stand's stun by default, as 1.16's Stand task was");
		EntityActionInstance standActionPort = new EntityActionInstance(null) {
			@Override
			public boolean isHeldBackBySummonLock() {
				return false;
			}
		};
		check(!standActionPort.isHeldBackByStandStun(),
				"an action not held back by its Stand's summon lock (1.16's StandAction) must not be held back by its stun");
		check(!new TimeStopAbility.TimeStopAction(null).isHeldBackByStandStun(),
				"the time stop, 1.16's StandAction, must not be held back by its Stand's stun");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
