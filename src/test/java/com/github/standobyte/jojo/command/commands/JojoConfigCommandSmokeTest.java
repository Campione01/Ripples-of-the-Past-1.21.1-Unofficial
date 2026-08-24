package com.github.standobyte.jojo.command.commands;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import com.github.standobyte.jojo.powersystem.standpower.StandStats;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.resources.ResourceLocation;

public final class JojoConfigCommandSmokeTest {
	private JojoConfigCommandSmokeTest() {}

	public static void run() {
		JsonObject metadata = JojoConfigCommand.packMetadata(48);
		check(metadata.getAsJsonObject("pack")
				.get("pack_format").getAsInt() == 48,
				"generated pack metadata lost the server data-pack format");

		StandStats stats = new StandStats.Builder()
				.power(10.0D, 14.0D)
				.speed(9.0D, 12.0D)
				.range(2.0D, 12.0D)
				.durability(8.0D, 11.0D)
				.precision(7.0D, 13.0D)
				.randomWeight(0.75D)
				.build();
		JsonObject template = JojoConfigCommand.standStatsTemplate(stats);
		check(template.size() == StandStats.StatWithValue.values().length,
				"generated Stand stat template is incomplete");
		check(template.get("power").getAsDouble() == 10.0D
					&& template.get("powerMax").getAsDouble() == 14.0D
					&& template.get("rangeEffective").getAsDouble() == 2.0D
					&& template.get("rangeMax").getAsDouble() == 12.0D
					&& template.get("randomWeight").getAsDouble() == 0.75D,
				"generated Stand stat values drifted");

		Path root = null;
		try {
			root = Files.createTempDirectory("rotp-jojoconfig-");
			ResourceLocation standId = ResourceLocation.fromNamespaceAndPath(
					"rotp_test", "test_stand");
			JojoConfigCommand.WriteResult first = JojoConfigCommand.writeStandStats(
					root, standId, stats, false);
			Path output = first.path();
			Path expected = root.resolve("data/rotp_test/jojostandpowers/"
					+ "test_stand/stats.json").toAbsolutePath().normalize();
			check(output.equals(expected),
					"generated Stand stat path does not match the loader contract");
			check(first.written(), "first generation did not create the file");
			JsonObject written = JsonParser.parseString(
					Files.readString(output)).getAsJsonObject();
			check(written.equals(template),
					"written Stand stat JSON differs from its template");

			Files.writeString(output, "{\"power\":123.0}");
			JojoConfigCommand.WriteResult skipped = JojoConfigCommand.writeStandStats(
					root, standId, stats, false);
			check(!skipped.written()
					&& JsonParser.parseString(Files.readString(output))
							.getAsJsonObject().get("power").getAsDouble() == 123.0D,
					"default generation overwrote an administrator edit");

			JojoConfigCommand.WriteResult forced = JojoConfigCommand.writeStandStats(
					root, standId, stats, true);
			check(forced.written()
					&& JsonParser.parseString(Files.readString(output))
							.getAsJsonObject().equals(template),
					"forced generation did not restore the default template");
		}
		catch (IOException exception) {
			throw new AssertionError("failed to exercise Stand stat generation",
					exception);
		}
		finally {
			deleteTree(root);
		}
	}

	private static void deleteTree(Path root) {
		if (root == null || !Files.exists(root)) {
			return;
		}
		try (var paths = Files.walk(root)) {
			paths.sorted(Comparator.reverseOrder()).forEach(path -> {
				try {
					Files.deleteIfExists(path);
				}
				catch (IOException exception) {
					throw new IllegalStateException(exception);
				}
			});
		}
		catch (IOException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
