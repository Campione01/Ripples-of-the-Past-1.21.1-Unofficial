package rotp.core.impl.powers.hamon.abilities;

import java.util.function.BiFunction;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;

import net.minecraft.resources.ResourceLocation;

/**
 * 1.16 held Sendo Overdrive (holdToFire(30, true)) and the S.Y.O. Barrage (holdToFire(60, false).holdType()) past
 * their full charge until the key was released; neither fired by itself. The barrage asked for no energy
 * (energyCost 0, holdEnergyCost 0) and its Instance.getWalkSpeed was 0. The ticking checks are in
 * HamonHoldReleaseGameTests.
 */
public final class HamonHoldReleaseSmokeTest {
	private HamonHoldReleaseSmokeTest() {}

	public static void run() {
		verifySendoHoldsFullCharge();
		verifyBarrageHoldsFullCharge();
		verifyBarrageEnergyAndWalkSpeed();
	}

	private static void verifySendoHoldsFullCharge() {
		HamonSendoOverdriveAbility sendo = create("sendo_overdrive", HamonSendoOverdriveAbility::new);
		sendo.hamonHoldToFire(30, false, 30, 5);
		HamonSendoOverdriveAbility.SendoOverdriveInstance held = charged(
				new HamonSendoOverdriveAbility.SendoOverdriveInstance(sendo), 30, 5);
		check(held.getPhase() == ActionPhase.WINDUP,
				"a fully charged Sendo Overdrive must stay held until the key is released, not fire by itself");
		held.onButtonStopHold();
		check(held.getPhase() == ActionPhase.PERFORM, "releasing a fully charged Sendo Overdrive must fire it");
	}

	private static void verifyBarrageHoldsFullCharge() {
		HamonSunlightYellowOverdriveBarrageAbility barrage = create("syo_barrage", HamonSunlightYellowOverdriveBarrageAbility::new);
		barrage.hamonHoldToFire(60, false, 60, 80);
		HamonSunlightYellowOverdriveBarrageAbility.SYOverdriveBarrageInstance held = charged(
				new HamonSunlightYellowOverdriveBarrageAbility.SYOverdriveBarrageInstance(barrage), 60, 80);
		check(held.getPhase() == ActionPhase.WINDUP,
				"a fully charged S.Y.O. Barrage must stay held until the key is released, not fire by itself");
		held.onButtonStopHold();
		check(held.getPhase() == ActionPhase.PERFORM, "releasing a fully charged S.Y.O. Barrage must fire it");
	}

	private static void verifyBarrageEnergyAndWalkSpeed() {
		HamonSunlightYellowOverdriveBarrageAbility barrage = create("syo_barrage_energy", HamonSunlightYellowOverdriveBarrageAbility::new);
		barrage.hamonHoldToFire(60, false, 60, 80);
		check(barrage.hasHamonEnergy(null, null),
				"1.16 S.Y.O. Barrage asked for no energy on the press or at the release (energyCost 0, holdEnergyCost 0)");
		HamonSunlightYellowOverdriveBarrageAbility.SYOverdriveBarrageInstance action =
				new HamonSunlightYellowOverdriveBarrageAbility.SYOverdriveBarrageInstance(barrage);
		action.onSetPhase(ActionPhase.PERFORM);
		check(action.userWalkSpeed == 0.0F, "1.16 kept the user still through the S.Y.O. Barrage (Instance.getWalkSpeed 0)");
	}

	private static <T extends EntityActionInstance> T charged(T action, int windup, int perform) {
		for (ActionPhase each : ActionPhase.values()) {
			action.phasesLength.put(each, 0.0F);
		}
		action.phasesLength.put(ActionPhase.WINDUP, (float) windup);
		action.phasesLength.put(ActionPhase.PERFORM, (float) perform);
		// The windup at its full length, as the tick that completes the charge leaves it.
		action.setPhase(ActionPhase.WINDUP, windup);
		return action;
	}

	private static <T extends HamonActionRuntimeAbility> T create(String name, BiFunction<AbilityType<T>, AbilityId, T> factory) {
		return new AbilityType<T>(ResourceLocation.fromNamespaceAndPath("jojo_ripples", "hold_release_" + name), factory)
				.createInstance(new AbilityId(null, ResourceLocation.fromNamespaceAndPath("jojo_ripples", "test_power"), name));
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
