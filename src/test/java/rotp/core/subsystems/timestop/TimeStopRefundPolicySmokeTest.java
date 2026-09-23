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
	 * The refund is a share of the stamina the start actually took, recorded per instance.
	 * Repricing it at the end paid Shadow The World back at the core's 225 for an 80 start,
	 * and read the cost multiplier again after the player could have changed it.
	 */
	private static void verifyRefundOfRecordedCharge() {
		// untrained Shadow The World: 80 charged for 5 ticks, resumed after 1
		checkClose(TimeStopState.getUnusedStartCostRefund(80.0F, 5, 1), 64.0F,
				"an early resume must refund a share of what the start took");
		checkClose(TimeStopState.getUnusedStartCostRefund(80.0F, 5, 0), 80.0F,
				"a stop ended before its first tick refunds the whole charge, no more");
		checkClose(TimeStopState.getUnusedStartCostRefund(80.0F, 5, -35), 80.0F,
				"a stop ended in its opening settle refunds the whole charge, no more");
		checkClose(TimeStopState.getUnusedStartCostRefund(80.0F, 5, 5), 0.0F,
				"a stop that ran its course refunds nothing");
		checkClose(TimeStopState.getUnusedStartCostRefund(80.0F, 5, 9), 0.0F,
				"elapsed ticks past the total refund nothing");
		// The World charged at x3 (noStandAbilityCooldown on at the start), half used
		checkClose(TimeStopState.getUnusedStartCostRefund(675.0F, 100, 50), 337.5F,
				"the refund must keep the multiplier in force at the start");
		for (float noCharge : new float[] {0.0F, -10.0F, Float.NaN, Float.POSITIVE_INFINITY}) {
			checkClose(TimeStopState.getUnusedStartCostRefund(noCharge, 5, 1), 0.0F,
					"an instance without a recorded charge must refund nothing: " + noCharge);
		}
		checkClose(TimeStopState.getUnusedStartCostRefund(80.0F, 0, 0), 0.0F,
				"a zero-tick instance must refund nothing");
		for (int elapsed = -2; elapsed <= 6; elapsed++) {
			check(TimeStopState.getUnusedStartCostRefund(80.0F, 5, elapsed) <= 80.0F,
					"a refund exceeded the recorded charge at elapsed=" + elapsed);
		}

		Path root = Path.of(System.getProperty("user.dir"));
		String state = read(root.resolve(
				"src/main/java/rotp/core/subsystems/timestop/TimeStopState.java"));
		String ability = read(root.resolve(
				"src/main/java/rotp/core/impl/stands/theworld/TimeStopAbility.java"));

		String refund = between(state,
				"private void refundUnusedTimeStopStartCost",
				"private void removeTimeStopEffectIfNoActiveInstance");
		check(refund.contains("getUnusedStartCostRefund(chargedStartCost, removed.totalTicks(), effectiveTicksPassed)")
						&& !refund.contains("getTimeStopStaminaCost(")
						&& !refund.contains("getTimeStopStaminaCostMultiplier("),
				"the refund must use the recorded charge, not a price taken at the end");

		String commit = between(state,
				"public boolean commitPreStart(TimeStopLifecycleEvent.PreStart event, @Nullable Ability startingAbility,\n"
						+ "            float chargedStartCost) {",
				"private void commitInstance");
		check(commit.contains("timeStopStartCharges.put(instance.id(), chargedStartCost);")
						&& commit.contains("timeStopStartCharges.remove(instance.id());")
						&& commit.indexOf("timeStopStartCharges.put(") < commit.indexOf("commitInstance(instance);"),
				"the commit must record (or clear) the start charge before the instance goes live");
		check(state.contains("return commitPreStart(event, startingAbility, 0.0F);")
						&& state.contains("return commitPreStart(event, null);"),
				"commits without a charge must record none");

		String settle = between(state,
				"private void applyTimeStopCooldowns(List<Instance> removedInstances)",
				"private String getCooldownAbilityName(Instance removed, StandPower power)");
		int charged = settle.indexOf("timeStopStartCharges.getOrDefault(removed.id(), 0.0F)");
		check(charged >= 0 && charged < settle.indexOf("timeStopStartCharges.remove(removed.id());")
						&& settle.contains("settlement.chargedStartCost());"),
				"the charge must be read before it is dropped, and refunded from the settlement");

		String start = between(ability,
				"private boolean startTimeStopAfterHold(LivingEntity user, boolean standAlreadySummoned, int requestedTimeStopTicks)",
				"public static class TimeStopAction");
		int before = start.indexOf("float staminaBefore = power.getStamina();");
		int consume = start.indexOf("power.consumeStamina(timeStopStaminaCost, false)");
		int record = start.indexOf("float chargedStartCost = Math.max(staminaBefore - power.getStamina(), 0.0F);");
		int commitCall = start.indexOf("state.commitPreStart(startEvent, this, chargedStartCost)");
		check(before >= 0 && before < consume && consume < record && record < commitCall,
				"the time stop must commit the stamina its start actually took");
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
