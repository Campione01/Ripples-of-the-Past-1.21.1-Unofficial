package rotp.core.impl.powers.hamon.abilities;

import java.util.function.BiFunction;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;

import net.minecraft.resources.ResourceLocation;

/**
 * 1.16 PowerBaseImpl.tickHeldAction re-checked a held Hamon technique on every tick (the free hand, the soap, the
 * stun), and stopHeldAction(false) dropped a failed charge without firing it. Sendo Overdrive's stoppedHolding sent
 * its wave on any stop. S.Y.O. and Scarlet Overdrive were held only until they fired, and their held energy check
 * never failed, nor did the S.Y.O. Barrage's. The checks that need a world are in HeldConditionRecheckGameTests.
 */
public final class HamonHeldConditionSmokeTest {
	private HamonHeldConditionSmokeTest() {}

	public static void run() {
		verifyChargedFailedCheckDrops();
		verifySendoFailedCheckFires();
		verifyOverdriveHeldUntilFired();
		verifyHeldEnergy();
	}

	private static void verifyChargedFailedCheckDrops() {
		HamonSunlightYellowOverdriveAbility syo = syo();
		EntityActionInstance charged = inPhase(new HamonSunlightYellowOverdriveAbility.SYOverdrive(syo), ActionPhase.WINDUP, 20);
		syo.stopHeldActionOnFailedCheck(charged);
		check(charged.isOver(), "a fully charged S.Y.O. that fails its check must be dropped, not fired");

		EntityActionInstance released = inPhase(new HamonSunlightYellowOverdriveAbility.SYOverdrive(syo), ActionPhase.WINDUP, 20);
		released.onButtonStopHold();
		check(!released.isOver() && released.getPhase() == ActionPhase.PERFORM,
				"a released S.Y.O. that passes its checks still fires");

		HamonScarletOverdriveAbility scarlet = create("scarlet_overdrive", HamonScarletOverdriveAbility::new);
		scarlet.hamonHoldToFire(8, true, 32, 5);
		EntityActionInstance scarletCharge = inPhase(new HamonScarletOverdriveAbility.ScarletOverdriveInstance(scarlet),
				ActionPhase.WINDUP, 20);
		scarlet.stopHeldActionOnGettingAttacked(scarletCharge);
		check(scarletCharge.isOver(), "a stopped Scarlet Overdrive charge must be dropped, not fired");

		HamonSunlightYellowOverdriveBarrageAbility barrage = create("syo_barrage", HamonSunlightYellowOverdriveBarrageAbility::new);
		barrage.hamonHoldToFire(60, false, 60, 80);
		EntityActionInstance barrageCharge = inPhase(
				new HamonSunlightYellowOverdriveBarrageAbility.SYOverdriveBarrageInstance(barrage), ActionPhase.WINDUP, 30);
		barrage.stopHeldActionOnFailedCheck(barrageCharge);
		check(barrageCharge.isOver(), "a S.Y.O. Barrage charge that fails its check must be dropped");

		HamonBubbleBarrierAbility barrier = create("bubble_barrier", HamonBubbleBarrierAbility::new);
		barrier.hamonHeldRuntime(50.0F, 0.3F).hamonHoldToFire(20, false, 20, 6);
		EntityActionInstance barrierCharge = inPhase(new HamonBubbleBarrierAbility.BubbleBarrierInstance(barrier),
				ActionPhase.WINDUP, 10);
		barrier.stopHeldActionOnFailedCheck(barrierCharge);
		check(barrierCharge.isOver(), "a Bubble Barrier charge that loses its soap must be dropped");
	}

	private static void verifySendoFailedCheckFires() {
		HamonSendoOverdriveAbility sendo = create("sendo_overdrive", HamonSendoOverdriveAbility::new);
		EntityActionInstance charging = inPhase(new HamonSendoOverdriveAbility.SendoOverdriveInstance(sendo), ActionPhase.WINDUP, 5);
		sendo.stopHeldActionOnFailedCheck(charging);
		check(!charging.isOver() && charging.getPhase() == ActionPhase.PERFORM,
				"1.16 Sendo Overdrive sent its wave on any stop of the hold, a failed check included");
	}

	private static void verifyOverdriveHeldUntilFired() {
		HamonSunlightYellowOverdriveAbility syo = syo();
		check(syo.isActionHeld(inPhase(new HamonSunlightYellowOverdriveAbility.SYOverdrive(syo), ActionPhase.WINDUP, 5)),
				"S.Y.O. is held while it charges");
		check(!syo.isActionHeld(inPhase(new HamonSunlightYellowOverdriveAbility.SYOverdrive(syo), ActionPhase.PERFORM, 2)),
				"the S.Y.O. punch after it fires is not held, so its checks do not stop the punch");
	}

	private static void verifyHeldEnergy() {
		check(syo().hasHeldEnergy(null, null),
				"1.16 S.Y.O. charged min(max / 40, energy), so its held energy check never failed");
		HamonSunlightYellowOverdriveBarrageAbility barrage = create("syo_barrage_energy", HamonSunlightYellowOverdriveBarrageAbility::new);
		check(barrage.hasHeldEnergy(null, null),
				"1.16 S.Y.O. Barrage asked for no held energy, so its held energy check never failed");
	}

	private static HamonSunlightYellowOverdriveAbility syo() {
		HamonSunlightYellowOverdriveAbility syo = create("sunlight_yellow_overdrive", HamonSunlightYellowOverdriveAbility::new);
		syo.hamonHoldToFire(10, true, 40, 10);
		return syo;
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
		return new AbilityType<T>(ResourceLocation.fromNamespaceAndPath("jojo_ripples", "held_condition_" + name), factory)
				.createInstance(new AbilityId(null, ResourceLocation.fromNamespaceAndPath("jojo_ripples", "test_power"), name));
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
