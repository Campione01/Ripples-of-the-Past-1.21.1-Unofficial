package com.github.standobyte.jojo.subsystems.timestop;

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
	}

	private static void verifyRefundAndNetworkContract() {
		Path root = Path.of(System.getProperty("user.dir"));
		String state = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/"
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
				"src/main/java/com/github/standobyte/jojo/"
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

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
