package com.github.standobyte.jojoimpl.powers.hamon.abilities;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class HamonOverdriveChargeReleaseSmokeTest {
	private static final int SUNLIGHT_MIN_CHARGE_TICKS = 10;
	private static final int SUNLIGHT_MAX_CHARGE_TICKS = 40;
	private static final int SCARLET_MIN_CHARGE_TICKS = 8;
	private static final int SCARLET_MAX_CHARGE_TICKS = 32;

	private HamonOverdriveChargeReleaseSmokeTest() {}

	public static void main(String[] args) {
		run();
	}

	public static void run() {
		verifyProductionContracts();
		verifyHoldBeyondSunlightCap();
		verifyScarletInheritanceAndCap();
		verifyLowEnergyChargeBehavior();
		verifyReleaseFiresOnce();
	}

	private static void verifyProductionContracts() {
		String sunlight = read("src/main/java/com/github/standobyte/jojoimpl/powers/hamon/abilities/"
				+ "HamonSunlightYellowOverdriveAbility.java");
		String actionTick = between(
				sunlight,
				"public void actionTick()",
				"public void actionPerformStart()");
		int capGuard = actionTick.indexOf("if (ticksHeld >= ability.maxChargeTicks)");
		int charge = actionTick.indexOf("ability.consumeChargeTick(user, ticksHeld)");
		int increment = actionTick.indexOf("ticksHeld++");
		check(capGuard >= 0 && capGuard < charge && charge < increment,
				"the charge cap must be checked before energy consumption and tick advancement");
		check(between(
				actionTick,
				"if (ticksHeld >= ability.maxChargeTicks)",
				"if (!level().isClientSide()")
				.contains("return;"),
				"a fully charged hold must leave the WINDUP tick without consuming energy");
		check(count(actionTick, "ability.consumeChargeTick(user, ticksHeld)") == 1
				&& count(actionTick, "ticksHeld++") == 1,
				"the WINDUP loop must have one charge and one effective-tick path");
		check(sunlight.contains("Math.min(ticksHeld, ability.maxChargeTicks)"),
				"the aura/UI charge estimate must remain clamped at the configured maximum");
		check(sunlight.contains(
				"getActualMaxEnergy(hamon) / Math.max(maxChargeTicks, 1)"),
				"the configured full-charge energy cost must remain spread across the capped ticks");

		String release = between(
				sunlight,
				"public void onButtonStopHold()",
				"private void performPunch(");
		check(release.contains("getPhase() == ActionPhase.WINDUP")
				&& count(release, "setPhaseStart(ActionPhase.PERFORM)") == 1
				&& release.contains("syncPhaseChanges();")
				&& release.contains("return;"),
				"release must transition from WINDUP to PERFORM through one synchronized path");

		String performStart = between(
				sunlight,
				"public void actionPerformStart()",
				"public void onButtonStopHold()");
		check(count(performStart, "fired = true;") == 1
				&& count(performStart, "ability.takeSpentEnergy(user)") == 1,
				"PERFORM start must mark and consume the accumulated charge once");
		check(count(actionTick, "performPunch(user);") == 1
				&& actionTick.contains("tick == 4 && !level().isClientSide()"),
				"the server punch must remain a one-shot PERFORM event");
		String sunlightPunch = between(
				sunlight,
				"private void performPunch(LivingEntity user)",
				"protected void doHamonAttack(");
		int sunlightAimRefresh = sunlightPunch.indexOf(
				"captureActionTargetFromAim(user)");
		int sunlightTargetRead = sunlightPunch.indexOf(
				"getActionTargetSnapshot(level())");
		check(sunlightAimRefresh >= 0
				&& sunlightAimRefresh < sunlightTargetRead,
				"Sunlight Yellow Overdrive must refresh the live aim before its authoritative hit");

		String beat = read("src/main/java/com/github/standobyte/jojoimpl/powers/hamon/abilities/"
				+ "HamonOverdriveBeatAbility.java");
		String beatPunch = between(
				beat,
				"private void punch(LivingEntity user)",
				"public void onActionCleared(");
		int beatAimRefresh = beatPunch.indexOf(
				"captureActionTargetFromAim(user)");
		int beatTargetRead = beatPunch.indexOf(
				"getActionTargetSnapshot(level)");
		check(beatAimRefresh >= 0 && beatAimRefresh < beatTargetRead,
				"Overdrive Beat must refresh the live aim before its authoritative hit");

		String consume = between(
				sunlight,
				"protected boolean consumeChargeTick(",
				"private void addSpentEnergy(");
		check(consume.contains("Math.min(tickCost, energyBefore)")
				&& consume.contains("if (consumed <= 0.0F)")
				&& consume.contains("return true;"),
				"low-energy charging must retain its partial-consumption and continuation behavior");
	}

	private static void verifyHoldBeyondSunlightCap() {
		ChargeReleaseModel sunlight = new ChargeReleaseModel(
				SUNLIGHT_MIN_CHARGE_TICKS, SUNLIGHT_MAX_CHARGE_TICKS, 100.0F, 2.5F);
		for (int tick = 0; tick < 100; tick++) {
			sunlight.holdTick();
		}
		check(sunlight.effectiveChargeTicks == SUNLIGHT_MAX_CHARGE_TICKS,
				"Sunlight effective charge exceeded 40 ticks");
		check(sunlight.energyConsumeCalls == SUNLIGHT_MAX_CHARGE_TICKS,
				"Sunlight consumed energy after reaching full charge");
		check(close(sunlight.energy, 0.0F) && close(sunlight.spentEnergy, 100.0F),
				"Sunlight full-charge cost changed while holding beyond the cap");
	}

	private static void verifyScarletInheritanceAndCap() {
		String scarlet = read("src/main/java/com/github/standobyte/jojoimpl/powers/hamon/abilities/"
				+ "HamonScarletOverdriveAbility.java");
		String compact = scarlet.replaceAll("\\s+", "");
		check(compact.contains("classHamonScarletOverdriveAbilityextendsHamonSunlightYellowOverdriveAbility")
				&& compact.contains("classScarletOverdriveInstanceextendsHamonSunlightYellowOverdriveAbility.SYOverdrive")
				&& compact.contains("ScarletOverdriveInstance::new,8,32,9")
				&& !scarlet.contains("void actionTick()"),
				"Scarlet Overdrive must inherit the capped Sunlight charge loop with its 32-tick maximum");

		ChargeReleaseModel scarletModel = new ChargeReleaseModel(
				SCARLET_MIN_CHARGE_TICKS, SCARLET_MAX_CHARGE_TICKS, 100.0F, 3.125F);
		for (int tick = 0; tick < 96; tick++) {
			scarletModel.holdTick();
		}
		check(scarletModel.effectiveChargeTicks == SCARLET_MAX_CHARGE_TICKS
				&& scarletModel.energyConsumeCalls == SCARLET_MAX_CHARGE_TICKS
				&& close(scarletModel.energy, 0.0F)
				&& close(scarletModel.spentEnergy, 100.0F),
				"Scarlet inherited charging did not stop at 32 ticks");
	}

	private static void verifyLowEnergyChargeBehavior() {
		ChargeReleaseModel lowEnergy = new ChargeReleaseModel(
				SUNLIGHT_MIN_CHARGE_TICKS, SUNLIGHT_MAX_CHARGE_TICKS, 2.5F, 1.0F);
		for (int tick = 0; tick < 12; tick++) {
			lowEnergy.holdTick();
		}
		check(lowEnergy.effectiveChargeTicks == 12 && lowEnergy.energyConsumeCalls == 12,
				"running out of energy must not stop the existing charge timer early");
		check(close(lowEnergy.energy, 0.0F) && close(lowEnergy.spentEnergy, 2.5F),
				"low-energy charging must consume only the available partial amount");
	}

	private static void verifyReleaseFiresOnce() {
		ChargeReleaseModel sunlight = new ChargeReleaseModel(
				SUNLIGHT_MIN_CHARGE_TICKS, SUNLIGHT_MAX_CHARGE_TICKS, 100.0F, 2.5F);
		for (int tick = 0; tick < 80; tick++) {
			sunlight.holdTick();
		}
		sunlight.release();
		sunlight.release();
		sunlight.performTick(0);
		sunlight.performTick(0);
		sunlight.performTick(4);
		sunlight.performTick(4);
		check(sunlight.performTransitions == 1
				&& sunlight.performStarts == 1
				&& sunlight.punches == 1,
				"releasing a fully charged hold must start and fire exactly once");
	}

	private static final class ChargeReleaseModel {
		private final int minChargeTicks;
		private final int maxChargeTicks;
		private final float tickCost;
		private int effectiveChargeTicks;
		private int energyConsumeCalls;
		private int performTransitions;
		private int performStarts;
		private int punches;
		private float energy;
		private float spentEnergy;
		private boolean windup = true;
		private boolean performStarted;
		private boolean punchPerformed;

		private ChargeReleaseModel(int minChargeTicks, int maxChargeTicks, float energy, float tickCost) {
			this.minChargeTicks = minChargeTicks;
			this.maxChargeTicks = maxChargeTicks;
			this.energy = energy;
			this.tickCost = tickCost;
		}

		private void holdTick() {
			if (!windup || effectiveChargeTicks >= maxChargeTicks) {
				return;
			}
			energyConsumeCalls++;
			float consumed = Math.min(tickCost, energy);
			energy -= consumed;
			spentEnergy += consumed;
			effectiveChargeTicks++;
		}

		private void release() {
			if (!windup) {
				return;
			}
			windup = false;
			if (effectiveChargeTicks >= minChargeTicks) {
				performTransitions++;
			}
		}

		private void performTick(int tick) {
			if (performTransitions == 0) {
				return;
			}
			if (tick == 0 && !performStarted) {
				performStarted = true;
				performStarts++;
			}
			if (tick == 4 && !punchPerformed) {
				punchPerformed = true;
				punches++;
			}
		}
	}

	private static String between(String source, String startToken, String endToken) {
		int start = source.indexOf(startToken);
		int end = source.indexOf(endToken, start + startToken.length());
		check(start >= 0 && end > start,
				"failed to locate source contract between " + startToken + " and " + endToken);
		return source.substring(start, end);
	}

	private static int count(String source, String token) {
		int count = 0;
		for (int index = source.indexOf(token); index >= 0;
				index = source.indexOf(token, index + token.length())) {
			count++;
		}
		return count;
	}

	private static String read(String path) {
		try {
			return Files.readString(Path.of(path));
		}
		catch (IOException error) {
			throw new AssertionError("failed to read " + path, error);
		}
	}

	private static boolean close(float actual, float expected) {
		return Math.abs(actual - expected) < 1.0E-6F;
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
