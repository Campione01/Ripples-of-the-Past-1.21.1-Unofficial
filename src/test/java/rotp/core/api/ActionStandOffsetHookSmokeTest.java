package rotp.core.api;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.impl.stands.silverchariot.SilverChariotSweepingAttackAbility;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.entity.StandEntity;

/**
 * FEATURE_ACTION_STAND_OFFSET_HOOK_V1: EntityActionInstance#updateStandOffset(StandEntity), a public overridable void
 * hook that does nothing by default, which the core's light and heavy attacks, Silver Chariot's sweep and barrages
 * override to keep 1.16's front3dOffset. StandFrontOffsetEveryTick116GameTests checks when the Stand's position update
 * asks it.
 */
final class ActionStandOffsetHookSmokeTest {
	private ActionStandOffsetHookSmokeTest() {}

	static void run() {
		check(RotpAddonApi.supportsFeature(RotpAddonApi.FEATURE_ACTION_STAND_OFFSET_HOOK_V1)
				&& "action_stand_offset_hook_v1".equals(RotpAddonApi.FEATURE_ACTION_STAND_OFFSET_HOOK_V1),
				"Action Stand offset hook feature missing");
		try {
			Method hook = EntityActionInstance.class.getDeclaredMethod("updateStandOffset", StandEntity.class);
			int modifiers = hook.getModifiers();
			check(Modifier.isPublic(modifiers) && !Modifier.isFinal(modifiers) && !Modifier.isStatic(modifiers)
					&& hook.getReturnType() == void.class,
					"EntityActionInstance#updateStandOffset(StandEntity) must be a public overridable void");
			for (Class<?> action : new Class<?>[] {
					StandEntityPunchAbility.StandEntityPunch.class,
					StandEntityHeavyPunchAbility.StandEntityHeavyPunch.class,
					StandEntityBarrageAbility.StandEntityBarrage.class,
					SilverChariotSweepingAttackAbility.SweepingStrike.class }) {
				check(action.getMethod("updateStandOffset", StandEntity.class).getDeclaringClass() == action,
						action.getSimpleName() + " must keep its front offset in updateStandOffset, as 1.16 asked it in"
						+ " StandEntity.updatePosition");
			}
		}
		catch (ReflectiveOperationException error) {
			throw new AssertionError("Action Stand offset hook API surface missing", error);
		}
		new EntityActionInstance(null).updateStandOffset(null);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
