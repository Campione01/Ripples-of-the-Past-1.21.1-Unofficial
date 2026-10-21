package rotp.core.api;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import rotp.core.impl.stands._entitybase.StandBearingShotAbility;
import rotp.core.impl.stands._entitybase.StandEntityGrabAbility;
import rotp.core.impl.stands._entitybase.StandEntityGrabThrowAbility;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchChargedAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;

/**
 * FEATURE_ABILITY_STAMINA_COST_V1: Ability#getStaminaCost(Power), 1.16's Action.getStaminaCost, a public overridable
 * float getter that the core's light and heavy punches override (AbilityStaminaCost116GameTests check the values), and
 * the port's own grab, grab throw, charged heavy punch and Bearing Shot (PortAbilityStaminaCostGameTests).
 */
final class AbilityStaminaCostSmokeTest {
	private AbilityStaminaCostSmokeTest() {}

	static void run() {
		check(RotpAddonApi.supportsFeature(RotpAddonApi.FEATURE_ABILITY_STAMINA_COST_V1)
				&& "ability_stamina_cost_v1".equals(RotpAddonApi.FEATURE_ABILITY_STAMINA_COST_V1),
				"Ability stamina cost feature missing");
		try {
			Method cost = Ability.class.getDeclaredMethod("getStaminaCost", Power.class);
			int modifiers = cost.getModifiers();
			check(Modifier.isPublic(modifiers) && !Modifier.isFinal(modifiers) && !Modifier.isStatic(modifiers)
					&& cost.getReturnType() == float.class,
					"Ability#getStaminaCost(Power) must be a public overridable float getter");
			check(StandEntityPunchAbility.class.getDeclaredMethod("getStaminaCost", Power.class).getReturnType() == float.class
					&& StandEntityHeavyPunchAbility.class.getDeclaredMethod("getStaminaCost", Power.class)
							.getReturnType() == float.class,
					"the core light and heavy punches must report their stamina cost");
			for (Class<?> portAbility : new Class<?>[] { StandEntityGrabAbility.class, StandEntityGrabThrowAbility.class,
					StandEntityHeavyPunchChargedAbility.class, StandBearingShotAbility.class }) {
				check(portAbility.getDeclaredMethod("getStaminaCost", Power.class).getReturnType() == float.class,
						portAbility.getSimpleName() + " must report the stamina a use of it takes");
			}
		}
		catch (ReflectiveOperationException error) {
			throw new AssertionError("Ability stamina cost API surface missing", error);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
