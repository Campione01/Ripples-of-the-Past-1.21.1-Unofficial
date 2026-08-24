package com.github.standobyte.jojo.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.power.ModStands;
import com.github.standobyte.jojo.powersystem.standpower.datapack.DataDrivenStandsLoader;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.SharedConstants;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class JojoConfigCommandGameTests {
	private JojoConfigCommandGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void standStatsCommandGeneratesCurrentDataPack(
			GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Path packRoot = server.getWorldPath(LevelResource.DATAPACK_DIR)
				.resolve("jojoconfig").toAbsolutePath().normalize();
		Path metadataPath = packRoot.resolve("pack.mcmeta");
		Path readmePath = packRoot.resolve("data")
				.resolve(JojoMod.MOD_ID)
				.resolve(DataDrivenStandsLoader.DIRECTORY)
				.resolve("README.txt");
		Path statsPath = packRoot.resolve("data")
				.resolve(JojoMod.MOD_ID)
				.resolve(DataDrivenStandsLoader.DIRECTORY)
				.resolve("star_platinum")
				.resolve("stats.json");
		boolean packExisted = Files.exists(packRoot);
		Map<Path, byte[]> originalFiles = snapshot(
				metadataPath, readmePath, statsPath);

		try {
			int generated = execute(server,
					"jojoconfig stand_stats force "
							+ JojoMod.MOD_ID + ":star_platinum");
			helper.assertTrue(generated == 1,
					"Forced single-Stand generation did not report one file");
			helper.assertTrue(Files.isRegularFile(metadataPath)
					&& Files.isRegularFile(readmePath)
					&& Files.isRegularFile(statsPath),
					"/jojoconfig did not create the complete world data pack");

			JsonObject metadata = readJson(metadataPath);
			int expectedPackFormat = SharedConstants.getCurrentVersion()
					.getPackVersion(PackType.SERVER_DATA);
			helper.assertTrue(metadata.getAsJsonObject("pack")
					.get("pack_format").getAsInt() == expectedPackFormat,
					"Generated data pack uses a stale pack format");

			JsonObject expectedStats = ModStands.STAR_PLATINUM.get()
					.getStandStats().makeConfigTemplate().getAsJsonObject();
			helper.assertTrue(readJson(statsPath).equals(expectedStats),
					"Generated stats.json does not match the current Stand loader schema");

			Files.writeString(statsPath, "{\"power\":123.0}");
			int skipped = execute(server,
					"jojoconfig stand_stats "
							+ JojoMod.MOD_ID + ":star_platinum");
			helper.assertTrue(skipped == 0
					&& readJson(statsPath).get("power").getAsDouble() == 123.0D,
					"Normal generation overwrote an administrator edit");

			execute(server, "jojoconfig stand_stats force "
					+ JojoMod.MOD_ID + ":star_platinum");
			helper.assertTrue(readJson(statsPath).equals(expectedStats),
					"Explicit force generation did not restore the default template");
			helper.succeed();
		}
		catch (IOException | CommandSyntaxException exception) {
			throw new AssertionError("/jojoconfig production command failed", exception);
		}
		finally {
			restore(packRoot, packExisted, originalFiles);
		}
	}

	private static int execute(MinecraftServer server, String command)
			throws CommandSyntaxException {
		return server.getCommands().getDispatcher().execute(
				command, server.createCommandSourceStack().withPermission(4));
	}

	private static JsonObject readJson(Path path) throws IOException {
		return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
	}

	private static Map<Path, byte[]> snapshot(Path... paths) {
		Map<Path, byte[]> snapshot = new LinkedHashMap<>();
		for (Path path : paths) {
			try {
				snapshot.put(path, Files.exists(path) ? Files.readAllBytes(path) : null);
			}
			catch (IOException exception) {
				throw new IllegalStateException("Could not snapshot " + path, exception);
			}
		}
		return snapshot;
	}

	private static void restore(
			Path packRoot, boolean packExisted, Map<Path, byte[]> snapshot) {
		try {
			if (!packExisted) {
				deleteTree(packRoot);
				return;
			}
			for (var entry : snapshot.entrySet()) {
				if (entry.getValue() == null) {
					Files.deleteIfExists(entry.getKey());
				}
				else {
					Files.createDirectories(entry.getKey().getParent());
					Files.write(entry.getKey(), entry.getValue());
				}
			}
		}
		catch (IOException exception) {
			throw new IllegalStateException("Could not restore GameTest data pack", exception);
		}
	}

	private static void deleteTree(Path root) throws IOException {
		if (!Files.exists(root)) {
			return;
		}
		try (var paths = Files.walk(root)) {
			for (Path path : paths.sorted((left, right) ->
					right.getNameCount() - left.getNameCount()).toList()) {
				Files.deleteIfExists(path);
			}
		}
	}
}
