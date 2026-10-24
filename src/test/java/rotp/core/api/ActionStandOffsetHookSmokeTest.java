package rotp.core.api;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;

import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.impl.stands.crazydiamond.CrazyDHealAbility;
import rotp.core.impl.stands.silverchariot.SilverChariotSweepingAttackAbility;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.entity.StandEntity;

/**
 * FEATURE_ACTION_STAND_OFFSET_HOOK_V1: EntityActionInstance#updateStandOffset(StandEntity), a public overridable void
 * hook that does nothing by default, which the core's light and heavy attacks, Silver Chariot's sweep and barrages
 * override to keep 1.16's front3dOffset, and Crazy Diamond's heal and revert to stand by their target. The Stand's
 * position update asks it on both sides, as 1.16's asked getOffsetFromUser; StandFrontOffsetEveryTick116GameTests checks
 * when it asks it.
 */
final class ActionStandOffsetHookSmokeTest {
	private ActionStandOffsetHookSmokeTest() {}

	private static final String STAND_ENTITY =
			"src/main/java/rotp/core/powersystem/standpower/entity/StandEntity.java";

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
					SilverChariotSweepingAttackAbility.SweepingStrike.class,
					CrazyDHealAbility.HealingAction.class }) {
				check(action.getMethod("updateStandOffset", StandEntity.class).getDeclaringClass() == action,
						action.getSimpleName() + " must keep its offset in updateStandOffset, as 1.16 asked it in"
						+ " StandEntity.updatePosition");
			}
		}
		catch (ReflectiveOperationException error) {
			throw new AssertionError("Action Stand offset hook API surface missing", error);
		}
		new EntityActionInstance(null).updateStandOffset(null);
		checkAskedOnBothSides();
	}

	// 1.16 StandEntity.updatePosition asked getOffsetFromUser on the client too: no side check guards the hook's call
	private static void checkAskedOnBothSides() {
		String source;
		try {
			source = Files.readString(Path.of(STAND_ENTITY));
		}
		catch (IOException error) {
			throw new AssertionError("Could not read " + STAND_ENTITY, error);
		}
		int method = source.indexOf("public void updatePosition(LivingEntity user) {");
		int call = method < 0 ? -1 : source.indexOf("curAction.updateStandOffset(this);", method);
		check(method >= 0 && call > method, "StandEntity.updatePosition no longer asks the action's updateStandOffset");
		int guard = source.lastIndexOf("if (", call);
		String condition = source.substring(guard, source.indexOf('\n', guard));
		check(guard > method && !condition.contains("isClientSide"),
				"StandEntity.updatePosition asks updateStandOffset on one side only: " + condition.strip());
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
