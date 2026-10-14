package rotp.core.subsystems.timestop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.world.level.ChunkPos;

public final class TimeStopRefundPolicySmokeTest {
	private TimeStopRefundPolicySmokeTest() {}

	public static void run() {
		TimeStopState.Instance defaults =
				new TimeStopState.Instance(
						1,
						80,
						80,
						new ChunkPos(2, 3),
						4,
						5,
						"default");
		check(defaults.refundUnusedStartCost(),
				"unused time-stop start cost must refund by default");

		TimeStopState.Instance noRefund =
				defaults.withRefundUnusedStartCost(false);
		check(!noRefund.refundUnusedStartCost(),
				"refund policy builder did not disable refunds");
		check(!noRefund.withTicksLeft(40, true)
						.refundUnusedStartCost()
				&& !noRefund.withTiming(40, 90)
						.refundUnusedStartCost()
				&& !noRefund.withArea(new ChunkPos(7, 8), 2)
						.refundUnusedStartCost()
				&& !noRefund.withVisualRoute("addon")
						.refundUnusedStartCost()
				&& !noRefund.withStaminaCostTick(2.0F)
						.refundUnusedStartCost()
				&& !noRefund.withResumeSoundUserId(9)
						.refundUnusedStartCost()
				&& !noRefund.withResumeSoundAndVoiceLineUserIds(
						9, 10).refundUnusedStartCost()
				&& !noRefund.withForceResumeVoiceLine(true)
						.refundUnusedStartCost()
				&& !noRefund.withStartupDelay(5)
						.refundUnusedStartCost()
				&& !noRefund.tickDown().refundUnusedStartCost(),
				"immutable time-stop updates lost the refund policy");

		verifyRefundAndNetworkContract();
		verifyRefundOfRecordedCharge();
	}

	/**
	 * The owner's rule (2026-10-14): an early end gives back (charged - unrefundable) x unused / total, never below 0,
	 * where the charge is what the start actually took and the unrefundable part is what the shortest stop costs
	 * (an immediate release) under the same policy and multiplier, both recorded when the stop starts. Repricing at
	 * the end paid Shadow The World back at the core's 225 for an 80 start, and read the multiplier again later.
	 */
	private static void verifyRefundOfRecordedCharge() {
		// the owner's example: a full 200-tick stop costs 1000, its shortest stop (20 ticks) 100
		checkClose(TimeStopState.getUnusedStartCostRefund(1000.0F, 100.0F, 200, 10), 855.0F,
				"a full charge resumed after 10 of 200 ticks gives back 900 x 190 / 200");
		checkClose(TimeStopState.getUnusedStartCostRefund(100.0F, 100.0F, 20, 0), 0.0F,
				"the shortest stop (start and resume at once) gives nothing back");
		checkClose(TimeStopState.getUnusedStartCostRefund(1000.0F, 100.0F, 200, 0), 900.0F,
				"a stop ended before its first tick gives back all but the shortest stop's price");
		checkClose(TimeStopState.getUnusedStartCostRefund(1000.0F, 100.0F, 200, -35), 900.0F,
				"a stop ended in its opening settle gives back all but the shortest stop's price");
		checkClose(TimeStopState.getUnusedStartCostRefund(1000.0F, 100.0F, 200, 200), 0.0F,
				"a stop that ran its course gives nothing back");
		checkClose(TimeStopState.getUnusedStartCostRefund(1000.0F, 100.0F, 200, 290), 0.0F,
				"elapsed ticks past the total give nothing back");
		// a half charge (100 of 200 ticks) pays half the full price
		checkClose(TimeStopState.getUnusedStartCostRefund(500.0F, 100.0F, 100, 50), 200.0F,
				"a released charge gives back its own share above the shortest stop");
		// The World charged at x3 (noStandAbilityCooldown on at the start), its shortest stop at x3 too, half used
		checkClose(TimeStopState.getUnusedStartCostRefund(675.0F, 27.0F, 100, 50), 324.0F,
				"the refund must keep the multiplier in force at the start");
		checkClose(TimeStopState.getUnusedStartCostRefund(80.0F, 120.0F, 5, 0), 0.0F,
				"a start that took less than the shortest stop's price gives nothing back, never a negative refund");
		for (float noCharge : new float[] {0.0F, -10.0F, Float.NaN, Float.POSITIVE_INFINITY}) {
			checkClose(TimeStopState.getUnusedStartCostRefund(noCharge, 0.0F, 5, 1), 0.0F,
					"an instance without a recorded charge must refund nothing: " + noCharge);
		}
		checkClose(TimeStopState.getUnusedStartCostRefund(80.0F, 0.0F, 0, 0), 0.0F,
				"a zero-tick instance must refund nothing");
		for (int elapsed = -2; elapsed <= 6; elapsed++) {
			check(TimeStopState.getUnusedStartCostRefund(80.0F, 8.0F, 5, elapsed) <= 72.0F,
					"a refund exceeded the refundable part of the charge at elapsed=" + elapsed);
		}

		Path root = Path.of(System.getProperty("user.dir"));
		String state = read(root.resolve(
				"src/main/java/rotp/core/subsystems/timestop/TimeStopState.java"));
		String ability = read(root.resolve(
				"src/main/java/rotp/core/impl/stands/theworld/TimeStopAbility.java"));

		String refund = between(state,
				"private void refundUnusedTimeStopStartCost",
				"private void removeTimeStopEffectIfNoActiveInstance");
		check(refund.contains("getUnusedStartCostRefund(startCharge.charged(), startCharge.unrefundable(),")
						&& refund.contains("removed.totalTicks(), effectiveTicksPassed)")
						&& !refund.contains("getTimeStopStaminaCost(")
						&& !refund.contains("getTimeStopStaminaCostMultiplier("),
				"the refund must use the recorded charge, not a price taken at the end");

		String commit = between(state,
				"public boolean commitPreStart(TimeStopLifecycleEvent.PreStart event, @Nullable Ability startingAbility,\n"
						+ "            float chargedStartCost, float unrefundableStartCost) {",
				"private void commitInstance");
		check(commit.contains("timeStopStartCharges.put(instance.id(), new StartCharge(chargedStartCost,")
						&& commit.contains("timeStopStartCharges.remove(instance.id());")
						&& commit.indexOf("timeStopStartCharges.put(") < commit.indexOf("commitInstance(instance);"),
				"the commit must record (or clear) the start charge and its unrefundable part before the instance goes live");
		check(state.contains("return commitPreStart(event, startingAbility, 0.0F);")
						&& state.contains("return commitPreStart(event, startingAbility, chargedStartCost, 0.0F);")
						&& state.contains("return commitPreStart(event, null);"),
				"commits without a charge must record none");

		// the last player leaving ends the stops with the same refund, no cooldown, no training, and drops the records
		String reset = between(state, "public void reset() {", "public boolean requestManualResume(int id)");
		check(reset.contains("refundUnusedTimeStopStartCost(power, removed, Math.max(removed.ticksPassed(), 0), charge);")
						&& reset.contains("timeStopStarters.clear();") && reset.contains("timeStopStartCharges.clear();")
						&& reset.indexOf("refundUnusedTimeStopStartCost(") < reset.indexOf("timeStopStartCharges.clear();")
						&& !reset.contains("applyTimeStopCooldown") && !reset.contains("TimeStopLearning."),
				"a reset must refund each start before dropping its record, and settle no cooldown or training");

		String settle = between(state,
				"private void applyTimeStopCooldowns(List<Instance> removedInstances)",
				"private String getCooldownAbilityName(Instance removed, StandPower power)");
		int charged = settle.indexOf("timeStopStartCharges.get(removed.id())");
		check(charged >= 0 && charged < settle.indexOf("timeStopStartCharges.remove(removed.id());")
						&& settle.contains("settlement.startCharge());"),
				"the charge must be read before it is dropped, and refunded from the settlement");

		String start = between(ability,
				"private boolean startTimeStopAfterHold(LivingEntity user, boolean standAlreadySummoned, int requestedTimeStopTicks)",
				"public static class TimeStopAction");
		int before = start.indexOf("float staminaBefore = power.getStamina();");
		int consume = start.indexOf("power.consumeStamina(timeStopStaminaCost, false)");
		int record = start.indexOf("float chargedStartCost = Math.max(staminaBefore - power.getStamina(), 0.0F);");
		int shortest = start.indexOf(
				"float unrefundableStartCost = getShortestStopStaminaCost(power, learningName, instance, chargeLength);");
		int commitCall = start.indexOf("state.commitPreStart(startEvent, this, chargedStartCost, unrefundableStartCost)");
		check(before >= 0 && before < consume && consume < record && record < shortest && shortest < commitCall,
				"the time stop must commit the stamina its start actually took, and its shortest stop's price");
		String shortestCost = between(ability, "float getShortestStopStaminaCost(", "public static class TimeStopAction");
		check(shortestCost.contains(
						"TimeStopLearning.getReleasedTimeStopTicks(TimeStopLearning.getSavedTimeStopTicks(power, learningName),")
						&& shortestCost.contains("TimeStopLearning.MIN_RELEASE_TIME_STOP_TICKS, chargeLength);")
						&& shortestCost.contains("TimeStopBehaviorPolicies.resolveStartupCost(")
						&& shortestCost.contains("return effectiveTimeStopStaminaCost(power,"),
				"the shortest stop is an immediate release of the press's charge, priced by the same policy and multiplier");
	}

	private static void verifyRefundAndNetworkContract() {
		Path root = Path.of(System.getProperty("user.dir"));
		String state = read(root.resolve(
				"src/main/java/rotp/core/"
				+ "subsystems/timestop/TimeStopState.java"));
		String refund = between(
				state,
				"private void refundUnusedTimeStopStartCost",
				"private void removeTimeStopEffectIfNoActiveInstance");
		check(refund.contains(
				"if (!removed.refundUnusedStartCost())")
				&& refund.indexOf(
						"if (!removed.refundUnusedStartCost())")
						< refund.indexOf("float refund"),
				"refund policy must gate unused-cost calculation");

		String packet = read(root.resolve(
				"src/main/java/rotp/core/"
				+ "network/s2c/TrTimeStopInstancePacket.java"));
		check(packet.contains(
				"boolean refundUnusedStartCost")
				&& packet.contains(
						"buf.writeBoolean(refundUnusedStartCost)")
				&& packet.contains(
						"payload.refundUnusedStartCost"),
				"time-stop refund policy is not propagated over the network");
	}

	private static String between(
			String source, String startToken, String endToken) {
		int start = source.indexOf(startToken);
		int end = source.indexOf(endToken, start);
		if (start < 0 || end < 0 || end <= start) {
			throw new AssertionError(
					"failed to locate source contract between "
							+ startToken + " and " + endToken);
		}
		return source.substring(start, end);
	}

	private static String read(Path path) {
		try {
			return Files.readString(path);
		}
		catch (IOException error) {
			throw new AssertionError("failed to read " + path, error);
		}
	}

	private static void checkClose(float actual, float expected, String message) {
		check(Math.abs(actual - expected) <= 0.0001F,
				message + ": expected=" + expected + ", actual=" + actual);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
