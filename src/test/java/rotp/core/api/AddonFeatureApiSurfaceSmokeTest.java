package rotp.core.api;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * A feature an add-on requires must stand for API that exists: an add-on built against it checks the
 * flag in its constructor instead of failing later with NoSuchMethodError on an older core.
 */
public final class AddonFeatureApiSurfaceSmokeTest {
	private AddonFeatureApiSurfaceSmokeTest() {}

	public static void main(String[] args) {
		run();
	}

	public static void run() {
		check(RotpAddonApi.supportsFeature(RotpAddonApi.FEATURE_TIME_STOP_ABILITY_TUNING_V1),
				"time-stop ability tuning is not advertised");
		requirePublic("rotp.core.impl.stands.theworld.TimeStopBlinkAbility", "setBaseTimeStopStaminaCosts",
				float.class, float.class);
		requirePublic("rotp.core.impl.stands.theworld.TimeStopBlinkAbility", "setBlinkSound",
				"net.minecraft.core.Holder");
		requireOverridable("rotp.core.impl.stands.theworld.TimeStopAbility", "shortensChargeAtResolveFour",
				"rotp.core.powersystem.standpower.StandPower");
		requirePublic("rotp.core.subsystems.timestop.TimeStopLearning", "onBlinkPunchTimeSkip",
				"rotp.core.powersystem.standpower.StandPower", int.class);

		check(RotpAddonApi.supportsFeature(RotpAddonApi.FEATURE_STAND_ATTACKER_RESOLVE_TIERS_V1),
				"attacker Resolve tiers are not advertised");
		requirePublic("rotp.core.mechanics.resolve.ResolveCounter", "addAttackerResolveMultTier",
				"net.minecraft.resources.ResourceLocation", int.class);

		check(RotpAddonApi.supportsFeature(RotpAddonApi.FEATURE_ENTITY_ACTION_HOOKS_V1),
				"entity action hooks are not advertised");
		String entityAction = "rotp.core.powersystem.ability.EntityActionAbility";
		String instance = "rotp.core.powersystem.entityaction.EntityActionInstance";
		requirePublic(entityAction, "checkHeldActionConditions", instance, "rotp.core.powersystem.Power");
		requirePublic(entityAction, "stopsOnHeavyAttack", instance);
		requirePublic(entityAction, "onHitByHeavyAttack", "net.minecraft.world.entity.Entity");
		requirePublic(entityAction, "setResetsAttackStrengthOnPerform");
	}

	private static void requirePublic(String owner, String name, Object... parameters) {
		Method method = find(owner, name, parameters);
		check(Modifier.isPublic(method.getModifiers()), owner + "#" + name + " must stay public");
	}

	private static void requireOverridable(String owner, String name, Object... parameters) {
		Method method = find(owner, name, parameters);
		int modifiers = method.getModifiers();
		check((Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers))
						&& !Modifier.isFinal(modifiers) && !Modifier.isStatic(modifiers),
				owner + "#" + name + " must stay overridable by add-ons");
	}

	private static Method find(String owner, String name, Object... parameters) {
		try {
			ClassLoader loader = AddonFeatureApiSurfaceSmokeTest.class.getClassLoader();
			Class<?>[] types = new Class<?>[parameters.length];
			for (int i = 0; i < parameters.length; i++) {
				types[i] = parameters[i] instanceof Class<?> type ? type
						: Class.forName((String) parameters[i], false, loader);
			}
			return Class.forName(owner, false, loader).getDeclaredMethod(name, types);
		}
		catch (ReflectiveOperationException e) {
			throw new AssertionError("advertised add-on API is missing: " + owner + "#" + name, e);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
