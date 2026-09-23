package rotp.core.subsystems.timestop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import rotp.core.api.timestop.TimeStopProgressionPolicy;

public final class TimeStopLearningContractSmokeTest {
	private static final float EPSILON = 0.0001F;

	private TimeStopLearningContractSmokeTest() {}

	public static void main(String[] args) {
		run();
	}

	public static void run() {
		check(TimeStopLearning.getLearnedTimeStopTicks(0.0F, 100) == 5,
				"zero training did not retain the five-tick saved duration");
		check(TimeStopLearning.getLearnedTimeStopTicks(0.99F, 100) == 5,
				"fractional training changed duration before a whole point");
		check(TimeStopLearning.getLearnedTimeStopTicks(1.0F, 100) == 6,
				"one training point did not add one saved duration tick");

		check(TimeStopLearning.getReleasedTimeStopTicks(5, 1.0F / 30.0F) == 1,
				"one-tick release did not resolve to one tick at the fresh cap");
		check(TimeStopLearning.getReleasedTimeStopTicks(5, 1.0F) == 5,
				"full charge did not retain the fresh five-tick cap");
		check(TimeStopLearning.getReleasedTimeStopTicks(25, 0.5F) == 13,
				"half charge did not round a 25-tick cap to 13 ticks");
		check(TimeStopLearning.getReleasedTimeStopTicks(25, 1.0F) == 25,
				"full charge did not retain a 25-tick cap");
		checkClose(TimeStopLearning.getTimeStopChargeRatio(1, 20), 0.05F,
				"one-tick hold lost its exact charge ratio");
		checkClose(TimeStopLearning.getTimeStopChargeRatio(10, 20), 0.5F,
				"half hold lost its exact charge ratio");
		checkClose(TimeStopLearning.getTimeStopChargeRatio(10, 20.5F),
				10.0F / 20.5F,
				"fractional phase length was narrowed before charge calculation");
		checkClose(TimeStopLearning.getTimeStopChargeRatio(20, 20), 1.0F,
				"full hold lost its exact charge ratio");
		check(TimeStopLearning.getReleasedTimeStopTicks(5, 1, 20) == 1,
				"fresh one-tick hold did not resolve proportionally");
		check(TimeStopLearning.getReleasedTimeStopTicks(5, 5, 20) == 2,
				"fresh quarter hold did not resolve proportionally");
		check(TimeStopLearning.getReleasedTimeStopTicks(5, 10, 20) == 3,
				"fresh half hold did not resolve proportionally");
		check(TimeStopLearning.getReleasedTimeStopTicks(5, 15, 20) == 4,
				"fresh three-quarter hold did not resolve proportionally");
		check(TimeStopLearning.getReleasedTimeStopTicks(5, 20, 20) == 5,
				"fresh full hold did not retain the trained duration");
		check(TimeStopLearning.getReleasedTimeStopTicks(25, 10, 20) == 13,
				"trained half hold did not scale from the trained duration");
		check(TimeStopLearning.getReleasedTimeStopTicks(25, 20, 20) == 25,
				"trained full hold did not retain the trained duration");

		checkClose(TimeStopLearning.getLearningPoints(
				TimeStopLearning.THE_WORLD_LEARNING_PER_TICK, 5), 0.5F,
				"The World lost fractional five-tick training");
		checkClose(TimeStopLearning.getLearningPoints(
				TimeStopLearning.STAR_PLATINUM_LEARNING_PER_TICK, 5), 1.25F,
				"Star Platinum lost fractional five-tick training");
		checkClose(TimeStopLearning.getLearningPoints(
				TimeStopLearning.THE_WORLD_LEARNING_PER_TICK, -5), 0.0F,
				"negative elapsed ticks awarded training");

		verifyZombieCap();
	}

	/** 1.16 TimeStop.getMaxTimeStopTicks checked high-saturation zombies first, against their own cap. */
	private static void verifyZombieCap() {
		check(TimeStopLearning.getZombieMaxTimeStopTicks(null) == TimeStopLearning.ZOMBIE_MAX_TIME_STOP_TICKS,
				"a Stand without a progression policy lost the core zombie cap");
		// Shadow The World: timeStopMaxTicks(60, 80, 1000, 70)
		check(TimeStopLearning.getZombieMaxTimeStopTicks(
						new TimeStopProgressionPolicy(60, 80, 70, 0.1F, 0.0F, 3.0F)) == 70,
				"a zombie must get the policy's zombie cap, not the enhanced one");
		check(TimeStopLearning.getZombieMaxTimeStopTicks(
						new TimeStopProgressionPolicy(60, 80, 0.1F, 0.0F, 3.0F)) == 80,
				"a five-value policy must keep zombies on the enhanced cap");

		String learning = read(Path.of(System.getProperty("user.dir")).resolve(
				"src/main/java/rotp/core/subsystems/timestop/TimeStopLearning.java"));
		int start = learning.indexOf("private static int getNormalMaxTimeStopTicks(StandPower power)");
		int end = learning.indexOf("static int getZombieMaxTimeStopTicks(", start);
		check(start >= 0 && end > start, "failed to locate getNormalMaxTimeStopTicks");
		String normalMax = learning.substring(start, end);
		int zombie = normalMax.indexOf("if (isHighSaturationZombie(user)) {\n\t\t\treturn getZombieMaxTimeStopTicks(policy);");
		check(zombie >= 0 && zombie < normalMax.indexOf("getPillarmanTimeStopTicks(user, enhancedMaxTicks)"),
				"high-saturation zombies must be capped first, by the zombie cap");
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
		check(Math.abs(actual - expected) <= EPSILON,
				message + ": expected=" + expected + ", actual=" + actual);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
