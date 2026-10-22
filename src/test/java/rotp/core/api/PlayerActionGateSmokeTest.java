package rotp.core.api;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import rotp.core.impl.powers.hamon.abilities.HamonOverdriveBeatAbility;
import rotp.core.impl.powers.hamon.abilities.HamonRebuffOverdriveAbility;
import rotp.core.impl.powers.hamon.abilities.HamonScarletOverdriveAbility;
import rotp.core.impl.powers.hamon.abilities.HamonSendoWaveKickAbility;
import rotp.core.impl.powers.hamon.abilities.HamonShockAbility;
import rotp.core.impl.powers.hamon.abilities.HamonSunlightYellowOverdriveAbility;
import rotp.core.impl.powers.hamon.abilities.HamonSunlightYellowOverdriveBarrageAbility;
import rotp.core.impl.powers.pillarman.abilities.PillarmanBladeDashAttackAbility;
import rotp.core.impl.powers.pillarman.abilities.PillarmanBladeSlashAbility;
import rotp.core.impl.powers.pillarman.abilities.PillarmanErraticBlazeKingAbility;
import rotp.core.impl.powers.pillarman.abilities.PillarmanHeavyPunchAbility;
import rotp.core.impl.powers.vampirism.abilities.VampirismClawLacerateAbility;
import rotp.core.impl.powers.vampirism.abilities.VampirismSpaceRipperStingyEyesAbility;
import rotp.core.impl.powers.zombie.abilities.ZombieClawLacerateAbility;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.EntityActionInstance;

/**
 * FEATURE_PLAYER_ACTION_GATE_V1: EntityActionInstance#isPlayerAction (false by default, true for the ports of 1.16's 13
 * IPlayerAction attacks) and Ability#canBeUsedDuringPlayerAction(EntityActionInstance), both public and overridable.
 * PlayerActionGate116GameTests checks the gate.
 */
final class PlayerActionGateSmokeTest {
	private PlayerActionGateSmokeTest() {}

	static void run() {
		check(RotpAddonApi.supportsFeature(RotpAddonApi.FEATURE_PLAYER_ACTION_GATE_V1)
				&& "player_action_gate_v1".equals(RotpAddonApi.FEATURE_PLAYER_ACTION_GATE_V1),
				"Player action gate feature missing");
		try {
			check(overridableBoolean(EntityActionInstance.class.getDeclaredMethod("isPlayerAction")),
					"EntityActionInstance#isPlayerAction() must be a public overridable boolean");
			check(overridableBoolean(Ability.class.getDeclaredMethod("canBeUsedDuringPlayerAction", EntityActionInstance.class)),
					"Ability#canBeUsedDuringPlayerAction(EntityActionInstance) must be a public overridable boolean");
		}
		catch (ReflectiveOperationException error) {
			throw new AssertionError("Player action gate API surface missing", error);
		}
		check(!new EntityActionInstance(null).isPlayerAction(),
				"an action must not be a continuous player action by default, as 1.16's Action was not");
		List<EntityActionInstance> playerActions = List.of(
				new HamonOverdriveBeatAbility.HamonOverdriveBeat(null),
				new HamonRebuffOverdriveAbility.HamonRebuffOverdrive(null),
				new HamonSendoWaveKickAbility.SendoWaveKickInstance(null),
				new HamonShockAbility.ShockInstance(null),
				new HamonSunlightYellowOverdriveAbility.SYOverdrive(null),
				new HamonScarletOverdriveAbility.ScarletOverdriveInstance(null),
				new HamonSunlightYellowOverdriveBarrageAbility.SYOverdriveBarrageInstance(null),
				new PillarmanBladeDashAttackAbility.BladeDashInstance(null),
				new PillarmanBladeSlashAbility.BladeSlashInstance(null),
				new PillarmanErraticBlazeKingAbility.ErraticBlazeKingInstance(null),
				new PillarmanHeavyPunchAbility.HeavyPunchInstance(null),
				new VampirismClawLacerateAbility.ClawLacerateInstance(null),
				new VampirismSpaceRipperStingyEyesAbility.SpaceRipperInstance(null),
				new ZombieClawLacerateAbility.ClawLacerateInstance(null));
		for (EntityActionInstance action : playerActions) {
			check(action.isPlayerAction(), action.getClass().getName() + " ports a 1.16 IPlayerAction");
		}
	}

	private static boolean overridableBoolean(Method method) {
		int modifiers = method.getModifiers();
		return Modifier.isPublic(modifiers) && !Modifier.isFinal(modifiers) && !Modifier.isStatic(modifiers)
				&& method.getReturnType() == boolean.class;
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
