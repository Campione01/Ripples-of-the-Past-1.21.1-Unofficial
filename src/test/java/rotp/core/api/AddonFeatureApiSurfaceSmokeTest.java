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
		requirePublic(entityAction, "canFireReleasedHold", instance);
		requirePublic(entityAction, "stopsOnHeavyAttack", instance);
		requirePublic(entityAction, "onHitByHeavyAttack", "net.minecraft.world.entity.Entity");
		requirePublic(entityAction, "setResetsAttackStrengthOnPerform");

		check(RotpAddonApi.supportsFeature(RotpAddonApi.FEATURE_STAND_USER_GUARD_V1),
				"the Stand's guard of its user is not advertised");
		String standEntity = "rotp.core.powersystem.standpower.entity.StandEntity";
		String noKnockback = "rotp.core.powersystem.standpower.entity.NoKnockbackOnBlocking";
		String living = "net.minecraft.world.entity.LivingEntity";
		requireOverridable(standEntity, "guardsHitsOnUser");
		requireOverridable(standEntity, "getDamageBlockMultiplier", instance);
		requirePublic(standEntity, "playStandBlockSound", "net.minecraft.world.damagesource.DamageSource");
		requirePublic(noKnockback, "setOneTickKbRes", living);
		requirePublic(noKnockback, "hasOneTickKbRes", living);
		requirePublic(noKnockback, "cancelHurtSound", living);

		check(RotpAddonApi.supportsFeature(RotpAddonApi.FEATURE_TIME_STOP_PUNCH_TUNING_V1),
				"TS punch tuning is not advertised");
		String tsPunch = "rotp.core.impl.stands.theworld.TheWorldTSPunchAbility";
		requirePublic(tsPunch, "setStaminaCost", float.class);
		requirePublic(tsPunch, "setTrainsTimeStop", boolean.class);

		check(RotpAddonApi.supportsFeature(RotpAddonApi.FEATURE_TIME_STOP_BLINK_TARGET_POS_V1),
				"the blink's entity target position hook is not advertised");
		requirePublic("rotp.core.impl.stands.theworld.TimeStopBlinkAbility", "setEntityTargetTeleportPos",
				java.util.function.BiFunction.class);
		requirePublic(tsPunch, "getStaminaCost");
		requirePublic(tsPunch, "trainsTimeStop");

		check(RotpAddonApi.supportsFeature(RotpAddonApi.FEATURE_TIME_STOP_HELD_WALK_SPEED_V1),
				"the time stop's held walk speed is not advertised");
		String timeStop = "rotp.core.impl.stands.theworld.TimeStopAbility";
		requirePublic(timeStop, "setHeldWalkSpeed", float.class);
		requirePublic(timeStop, "getHeldWalkSpeed");

		String ability = "rotp.core.powersystem.ability.Ability";
		String power = "rotp.core.powersystem.Power";
		check(RotpAddonApi.supportsFeature(RotpAddonApi.FEATURE_ABILITY_REQUIRED_RESOLVE_LEVEL_V1),
				"the ability's required Resolve level is not advertised");
		requirePublic(ability, "getRequiredResolveLevel", power);

		check(RotpAddonApi.supportsFeature(RotpAddonApi.FEATURE_ABILITY_SHOUTS_V1),
				"the ability shout sneak rule is not advertised");
		requirePublic(ability, "sayShout", living, "net.minecraft.core.Holder");
		requirePublic(ability, "sayShoutOf", "rotp.core.powersystem.entityaction.type.EntityActionType", living,
				"net.minecraft.core.Holder");
		requirePublic(ability, "setPlaysVoiceLineOnSneak");
		requireOverridable(ability, "playsVoiceLineOnSneak");
		requirePublic(ability, "skipsShoutWhileSneaking", living);
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
