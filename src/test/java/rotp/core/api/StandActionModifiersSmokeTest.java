package rotp.core.api;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.effect.StandEffectInstance;

/**
 * FEATURE_STAND_ACTION_MODIFIERS_V1: an add-on's StandEffectInstance with isFromStandAction attaches to the Stand's
 * running action (EntityActionInstance#getPunchModifiers) and its overridable onModifiedActionTick runs right after the
 * action's own tick, as 1.16's StandEntityActionModifier did on its task (StandActionModifierGameTests run it).
 */
final class StandActionModifiersSmokeTest {
	private StandActionModifiersSmokeTest() {}

	static void run() {
		check(RotpAddonApi.supportsFeature(RotpAddonApi.FEATURE_STAND_ACTION_MODIFIERS_V1)
				&& "stand_action_modifiers_v1".equals(RotpAddonApi.FEATURE_STAND_ACTION_MODIFIERS_V1),
				"Stand action modifiers feature missing");
		try {
			Method hook = StandEffectInstance.class.getDeclaredMethod("onModifiedActionTick", EntityActionInstance.class);
			int modifiers = hook.getModifiers();
			check(Modifier.isPublic(modifiers) && !Modifier.isFinal(modifiers) && !Modifier.isStatic(modifiers)
					&& hook.getReturnType() == void.class,
					"StandEffectInstance#onModifiedActionTick(EntityActionInstance) must be a public overridable hook");
			Field fromStandAction = StandEffectInstance.class.getField("isFromStandAction");
			Field standAction = StandEffectInstance.class.getField("standAction");
			check(fromStandAction.getType() == boolean.class && standAction.getType() == EntityActionInstance.class,
					"StandEffectInstance must keep its public isFromStandAction flag and standAction");
			Method modifiersOf = EntityActionInstance.class.getMethod("getPunchModifiers");
			check(modifiersOf.getReturnType() == List.class, "EntityActionInstance#getPunchModifiers must stay public");
		}
		catch (ReflectiveOperationException error) {
			throw new AssertionError("Stand action modifier API surface missing", error);
		}

		String action = squash(read("src/main/java/rotp/core/powersystem/entityaction/EntityActionInstance.java"));
		check(action.contains("actionPerformEnd();}}tickModifiers();}"),
				"the action must tick its modifiers last in its own tick, after its perform start and end");
		check(action.contains("privatevoidtickModifiers(){if(punchModifiers!=null&&!punchModifiers.isEmpty()&&!isOver()){"
				+ "for(StandEffectInstancemodifier:List.copyOf(punchModifiers)){if(!modifier.isStopped()){"
				+ "modifier.onModifiedActionTick(this);}}}}"),
				"the action must call onModifiedActionTick on each modifier not stopped, while it is not over");
	}

	private static String read(String relativePath) {
		Path path = Path.of(System.getProperty("user.dir")).resolve(relativePath);
		try {
			return Files.readString(path);
		}
		catch (IOException error) {
			throw new AssertionError("failed to read " + path, error);
		}
	}

	private static String squash(String source) {
		return source.replaceAll("\\s+", "");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
