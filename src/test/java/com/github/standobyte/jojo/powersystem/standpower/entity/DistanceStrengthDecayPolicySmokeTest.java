package com.github.standobyte.jojo.powersystem.standpower.entity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DistanceStrengthDecayPolicySmokeTest {
	private DistanceStrengthDecayPolicySmokeTest() {}

	public static void main(String[] args) {
		check(StandStatFormulas.rangeStrengthFactor(true, 2, 10, 2) == 1F,
				"close-range stands must keep full strength inside effective range");
		check(StandStatFormulas.rangeStrengthFactor(true, 2, 10, 10) == 0.25F,
				"close-range stands must decay to the existing floor at max range");
		check(StandStatFormulas.rangeStrengthFactor(false, 50, 100, 100) == 1F,
				"native long-range stands must not receive close-range strength decay");

		Path root = Path.of(System.getProperty("user.dir"));
		String type = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/powersystem/standpower/entity/EntityStandType.java"));
		check(type.contains("new DefaultedValue.Bool(true)"),
				"distance strength decay must remain enabled by default");
		check(type.contains("\"distanceStrengthDecayEnabled\"")
				&& type.contains("distanceStrengthDecayEnabled.reset()"),
				"distance strength decay must round-trip through stand config and restore its type default");

		String entity = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/powersystem/standpower/entity/StandEntity.java"));
		check(entity.contains("entityStandType.usesDistanceStrengthDecay()"),
				"stand entities must initialize the type policy");
		check(entity.contains("rangeStrengthFactor(distanceStrengthDecayEnabled,"),
				"stand strength must use the initialized type policy");
		check(entity.contains("double range = getMaxRangeForMovement(user);"),
				"manual movement must remain capped independently by max range");

		String hierophant = read(root.resolve(
				"src/main/java/com/github/standobyte/jojoimpl/stands/StandInitHierophantGreen.java"));
		check(hierophant.contains(".range(50, 100)")
				&& hierophant.contains(".distanceStrengthDecay(false)"),
				"Hierophant Green must retain its range cap without close-range strength decay");
	}

	private static String read(Path path) {
		try {
			return Files.readString(path);
		}
		catch (IOException e) {
			throw new AssertionError("failed to read " + path, e);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
