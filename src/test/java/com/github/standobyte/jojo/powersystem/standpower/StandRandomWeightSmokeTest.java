package com.github.standobyte.jojo.powersystem.standpower;

import com.google.gson.JsonObject;

import net.minecraft.util.RandomSource;

public final class StandRandomWeightSmokeTest {
	private StandRandomWeightSmokeTest() {}

	public static void run() {
		StandStats defaultStats = stats(StandStats.DEFAULT_RANDOM_WEIGHT);
		check(defaultStats.getRandomWeight()
						== StandStats.DEFAULT_RANDOM_WEIGHT,
				"default Stand random weight changed");

		StandStats configuredStats = stats(0.3D);
		check(configuredStats.getRandomWeight() == 0.3D,
				"builder Stand random weight was not retained");
		JsonObject template = configuredStats.makeConfigTemplate()
				.getAsJsonObject();
		check(template.get("randomWeight").getAsDouble() == 0.3D,
				"Stand random weight is missing from the config template");
		JsonObject override = new JsonObject();
		override.addProperty("randomWeight", 1.25D);
		configuredStats.applyConfig(override);
		check(configuredStats.getRandomWeight() == 1.25D,
				"configured Stand random weight was not applied");
		configuredStats.restoreDefaults();
		check(configuredStats.getRandomWeight() == 0.3D,
				"Stand random weight default was not restored");

		double[] weighted = {0.0D, 3.0D, 1.0D};
		RandomSource random = RandomSource.create(0x5EEDL);
		int commonSelections = 0;
		int rareSelections = 0;
		for (int i = 0; i < 20_000; i++) {
			int selected = StandUtil.randomWeightedIndex(
					weighted, random);
			check(selected != 0,
					"zero-weight Stand was selected");
			if (selected == 1) {
				commonSelections++;
			}
			else if (selected == 2) {
				rareSelections++;
			}
		}
		double ratio = (double) commonSelections / rareSelections;
		check(ratio > 2.8D && ratio < 3.2D,
				"weighted Stand selection ratio drifted: " + ratio);

		check(StandUtil.randomWeightedIndex(
						new double[] {
								0.0D, -1.0D, Double.NaN
						},
						RandomSource.create(1L))
				== -1,
				"invalid-only Stand pool should not select a Stand");
	}

	private static StandStats stats(double randomWeight) {
		return new StandStats.Builder()
				.power(1.0D)
				.speed(1.0D)
				.range(1.0D, 1.0D)
				.durability(1.0D)
				.precision(1.0D)
				.randomWeight(randomWeight)
				.build();
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
