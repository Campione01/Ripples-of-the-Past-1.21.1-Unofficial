package rotp.core.api;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import rotp.core.impl.powers.hamon.abilities.HamonBreathAbility;
import rotp.core.impl.powers.pillarman.abilities.PillarmanAbsorptionAbility;
import rotp.core.impl.powers.vampirism.abilities.VampirismBloodDrainAbility;
import rotp.core.impl.powers.vampirism.abilities.VampirismFreezeAbility;
import rotp.core.impl.powers.zombie.abilities.ZombieDevourAbility;
import rotp.core.powersystem.entityaction.EntityActionInstance;

/**
 * FEATURE_HELD_ACTION_RULES_PER_POWER_V1: EntityActionInstance#heldAllowsOtherAbilities, a public overridable boolean
 * (1.16 Action.heldAllowsOtherAction) that answers as isHoldTypeStandAction by default and is true for the four non-Stand
 * holds whose 1.16 action allowed others. HeldActionRulesPerPower116GameTests checks the rules it drives.
 */
final class HeldActionRulesPerPowerSmokeTest {
	private HeldActionRulesPerPowerSmokeTest() {}

	static void run() {
		check(RotpAddonApi.supportsFeature(RotpAddonApi.FEATURE_HELD_ACTION_RULES_PER_POWER_V1)
				&& "held_action_rules_per_power_v1".equals(RotpAddonApi.FEATURE_HELD_ACTION_RULES_PER_POWER_V1),
				"Held action rules per power feature missing");
		try {
			Method hook = EntityActionInstance.class.getDeclaredMethod("heldAllowsOtherAbilities");
			int modifiers = hook.getModifiers();
			check(Modifier.isPublic(modifiers) && !Modifier.isFinal(modifiers) && !Modifier.isStatic(modifiers)
					&& hook.getReturnType() == boolean.class,
					"EntityActionInstance#heldAllowsOtherAbilities() must be a public overridable boolean");
		}
		catch (ReflectiveOperationException error) {
			throw new AssertionError("Held action rules per power API surface missing", error);
		}
		check(!new EntityActionInstance(null).heldAllowsOtherAbilities(),
				"a held action must let no other action of its power through by default, as 1.16's Action did");
		EntityActionInstance holdType = new EntityActionInstance(null) {
			@Override
			public boolean isHoldTypeStandAction() {
				return true;
			}
		};
		check(holdType.heldAllowsOtherAbilities(),
				"a hold-type Stand action must let the other actions of its power through, as 1.16's StandEntityAction did");
		check(new VampirismBloodDrainAbility.BloodDrainInstance(null).heldAllowsOtherAbilities()
				&& new VampirismFreezeAbility.FreezeInstance(null).heldAllowsOtherAbilities()
				&& new ZombieDevourAbility.DevourInstance(null).heldAllowsOtherAbilities()
				&& new PillarmanAbsorptionAbility.AbsorptionInstance(null).heldAllowsOtherAbilities(),
				"Blood Drain, Freeze, Zombie Devour and Pillar Man Absorption allowed other actions in 1.16");
		check(!new HamonBreathAbility.BreathInstance(null).heldAllowsOtherAbilities(),
				"Hamon Breath let no other Hamon action through in 1.16");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
