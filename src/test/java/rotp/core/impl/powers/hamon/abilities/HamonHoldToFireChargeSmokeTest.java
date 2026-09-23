package rotp.core.impl.powers.hamon.abilities;

import java.util.function.BiFunction;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;

import net.minecraft.resources.ResourceLocation;

/**
 * 1.16 ran a held action's hold tick on every tick from the press until it fired or was released, so a
 * hold-to-fire Hamon technique ticks while it charges and no longer once it has fired. Sendo Overdrive fired on
 * any release; releasing before the full charge only narrowed its sparks.
 */
public final class HamonHoldToFireChargeSmokeTest {
	private HamonHoldToFireChargeSmokeTest() {}

	public static void run() {
		verifyHeldTickPhases();
		verifyChargeWalkSpeed();
		verifySendoEarlyRelease();
	}

	private static void verifyHeldTickPhases() {
		// As HamonPowerType sets them up: heldUtility/heldCombat, then hamonHoldToFire.
		HamonHypnosisAbility hypnosis = create("hypnosis", HamonHypnosisAbility::new);
		hypnosis.hamonHeldRuntime(15.0F, 1.0F).hamonHoldToFire(60, false, 60, 6);
		HamonBubbleBarrierAbility barrier = create("bubble_barrier", HamonBubbleBarrierAbility::new);
		barrier.hamonHeldRuntime(50.0F, 0.3F).hamonHoldToFire(20, false, 20, 6);
		for (HamonActionRuntimeAbility charged : new HamonActionRuntimeAbility[] { hypnosis, barrier }) {
			String name = charged.getAbilityId().nameInMoveset();
			check(charged.isHeldTickPhase(ActionPhase.WINDUP), name + " must run its hold tick while it charges");
			check(!charged.isHeldTickPhase(ActionPhase.PERFORM) && !charged.isHeldTickPhase(ActionPhase.RECOVERY),
					name + " is no longer held once it has fired");
		}

		HamonActionRuntimeAbility holding = create("held", (type, abilityId) ->
				new HamonActionRuntimeAbility(type, abilityId, HamonActionRuntimeAbility.HamonHeldActionInstance::new));
		holding.hamonHeldRuntime(5.0F, 0.9999F);
		check(!holding.isHeldTickPhase(ActionPhase.WINDUP) && holding.isHeldTickPhase(ActionPhase.PERFORM),
				"a hold-only technique holds while it performs");

		HamonActionRuntimeAbility continuing = create("continuing", (type, abilityId) ->
				new HamonActionRuntimeAbility(type, abilityId, HamonActionRuntimeAbility.HamonHeldActionInstance::new));
		continuing.hamonHoldToFire(10, true, 40, 10);
		check(continuing.isHeldTickPhase(ActionPhase.WINDUP) && continuing.isHeldTickPhase(ActionPhase.PERFORM),
				"a technique that keeps holding after it fires holds in both phases");
	}

	private static void verifyChargeWalkSpeed() {
		HamonBubbleBarrierAbility barrier = create("bubble_barrier_speed", HamonBubbleBarrierAbility::new);
		barrier.hamonHeldRuntime(50.0F, 0.3F).hamonHoldToFire(20, false, 20, 6);
		HamonBubbleBarrierAbility.BubbleBarrierInstance action = new HamonBubbleBarrierAbility.BubbleBarrierInstance(barrier);
		action.onSetPhase(ActionPhase.WINDUP);
		check(action.userWalkSpeed == 0.3F, "the held walk speed applies while the barrier charges");
		action.onSetPhase(ActionPhase.PERFORM);
		check(action.userWalkSpeed == 1.0F, "the held walk speed ends once the barrier fires");
	}

	private static void verifySendoEarlyRelease() {
		HamonSendoOverdriveAbility sendo = create("sendo_overdrive", HamonSendoOverdriveAbility::new);
		HamonSendoOverdriveAbility.SendoOverdriveInstance early = inPhase(
				new HamonSendoOverdriveAbility.SendoOverdriveInstance(sendo), ActionPhase.WINDUP, 3);
		early.onButtonStopHold();
		check(!early.isOver() && early.getPhase() == ActionPhase.PERFORM,
				"a Sendo Overdrive released before its full charge must still fire");

		HamonSendoOverdriveAbility.SendoOverdriveInstance fired = inPhase(
				new HamonSendoOverdriveAbility.SendoOverdriveInstance(sendo), ActionPhase.PERFORM, 2);
		fired.onButtonStopHold();
		check(!fired.isOver() && fired.getPhase() == ActionPhase.PERFORM && fired.getPhaseTick() == 2,
				"releasing after Sendo Overdrive fired changes nothing");

		HamonHypnosisAbility hypnosis = create("hypnosis_release", HamonHypnosisAbility::new);
		hypnosis.hamonHeldRuntime(15.0F, 1.0F).hamonHoldToFire(60, false, 60, 6);
		HamonHypnosisAbility.HypnosisInstance dropped = inPhase(new HamonHypnosisAbility.HypnosisInstance(hypnosis),
				ActionPhase.WINDUP, 30);
		dropped.onButtonStopHold();
		check(dropped.isOver(), "a Hypnosis released before its full charge must not fire");
	}

	private static <T extends EntityActionInstance> T inPhase(T action, ActionPhase phase, int tick) {
		for (ActionPhase each : ActionPhase.values()) {
			action.phasesLength.put(each, 100.0F);
		}
		action.setPhase(phase, tick);
		check(action.getPhase() == phase, "could not put the test action in " + phase);
		return action;
	}

	private static <T extends HamonActionRuntimeAbility> T create(String name, BiFunction<AbilityType<T>, AbilityId, T> factory) {
		return new AbilityType<T>(ResourceLocation.fromNamespaceAndPath("jojo_ripples", "hold_to_fire_" + name), factory)
				.createInstance(new AbilityId(null, ResourceLocation.fromNamespaceAndPath("jojo_ripples", "test_power"), name));
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
