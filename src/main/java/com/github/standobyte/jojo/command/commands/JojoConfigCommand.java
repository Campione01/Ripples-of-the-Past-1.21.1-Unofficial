package com.github.standobyte.jojo.command.commands;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;

import com.github.standobyte.jojo.command.argument.StandArgument;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.core.JojoRegistries;
import com.github.standobyte.jojo.powersystem.standpower.StandStats;
import com.github.standobyte.jojo.powersystem.standpower.datapack.DataDrivenStandsLoader;
import com.github.standobyte.jojo.powersystem.standpower.type.StandType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;

import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.level.storage.LevelResource;

public final class JojoConfigCommand {
	public static final String LITERAL = "jojoconfig";
	private static final String PACK_NAME = "jojoconfig";
	private static final String STATS_FILE = "stats.json";
	private static final Gson GSON = new GsonBuilder()
			.setPrettyPrinting()
			.disableHtmlEscaping()
			.create();
	private static final DynamicCommandExceptionType ERROR_GENERATE =
			new DynamicCommandExceptionType(reason -> Component.translatable(
					"commands.jojoconfig.failed", reason));

	private JojoConfigCommand() {}

	public static void register(
			CommandDispatcher<CommandSourceStack> dispatcher,
			CommandBuildContext context) {
		dispatcher.register(Commands.literal(LITERAL)
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("stand_stats")
						.executes(command -> generateAll(command.getSource(), false))
						.then(Commands.literal("force")
								.executes(command -> generateAll(command.getSource(), true))
								.then(Commands.argument("stand", StandArgument.stand(context))
										.executes(command -> generateSingle(
												command.getSource(),
												StandArgument.getStand(command, "stand"),
												true))))
						.then(Commands.argument("stand", StandArgument.stand(context))
								.executes(command -> generateSingle(
										command.getSource(),
										StandArgument.getStand(command, "stand"),
										false))))
				.then(Commands.literal("folder_link")
						.executes(command -> showFolder(command.getSource()))));
		JojoCommandsCommand.addCommand(LITERAL);
	}

	private static int generateAll(CommandSourceStack source, boolean force)
			throws CommandSyntaxException {
		try {
			MinecraftServer server = source.getServer();
			Path packRoot = ensurePackBase(server);
			List<StandType> stands = JojoRegistries.DEFAULT_STANDS_REG
					.entrySet().stream()
					.map(entry -> entry.getValue())
					.filter(StandType::isEnabled)
					.sorted(Comparator.comparing(stand -> stand.getId().toString()))
					.toList();
			int written = 0;
			for (StandType stand : stands) {
				if (writeStandStats(packRoot, stand.getId(),
						stand.getStandStats(), force).written()) {
					written++;
				}
			}
			writeReadme(packRoot);
			int writtenCount = written;
			int skipped = stands.size() - writtenCount;
			source.sendSuccess(() -> Component.translatable(
					"commands.jojoconfig.generated_all",
					writtenCount, skipped, pathComponent(source, packRoot)), true);
			sendReloadHint(source);
			return writtenCount;
		}
		catch (Exception exception) {
			throw generationFailure(exception);
		}
	}

	private static int generateSingle(
			CommandSourceStack source, StandType stand, boolean force)
			throws CommandSyntaxException {
		if (stand == null) {
			throw generationFailure(new IllegalArgumentException("Unknown Stand"));
		}
		try {
			Path packRoot = ensurePackBase(source.getServer());
			WriteResult result = writeStandStats(
					packRoot, stand.getId(), stand.getStandStats(), force);
			writeReadme(packRoot);
			source.sendSuccess(() -> Component.translatable(
					result.written()
							? "commands.jojoconfig.generated_single"
							: "commands.jojoconfig.skipped_single",
					stand.getId().toString(),
					pathComponent(source, result.path())), true);
			sendReloadHint(source);
			return result.written() ? 1 : 0;
		}
		catch (Exception exception) {
			throw generationFailure(exception);
		}
	}

	private static int showFolder(CommandSourceStack source)
			throws CommandSyntaxException {
		try {
			Path packRoot = ensurePackBase(source.getServer());
			source.sendSuccess(() -> Component.translatable(
					"commands.jojoconfig.folder",
					pathComponent(source, packRoot)), false);
			return 1;
		}
		catch (Exception exception) {
			throw generationFailure(exception);
		}
	}

	private static void sendReloadHint(CommandSourceStack source) {
		source.sendSuccess(() -> Component.translatable(
				"commands.jojoconfig.reload_hint").withStyle(ChatFormatting.GRAY),
				false);
	}

	private static Path ensurePackBase(MinecraftServer server)
			throws IOException {
		Path packRoot = server.getWorldPath(LevelResource.DATAPACK_DIR)
				.resolve(PACK_NAME).toAbsolutePath().normalize();
		Files.createDirectories(packRoot);
		int packFormat = SharedConstants.getCurrentVersion()
				.getPackVersion(PackType.SERVER_DATA);
		writeJsonFile(packRoot.resolve("pack.mcmeta"), packMetadata(packFormat));
		return packRoot;
	}

	static JsonObject packMetadata(int packFormat) {
		JsonObject pack = new JsonObject();
		pack.addProperty("pack_format", packFormat);
		pack.addProperty("description",
				"Editable Stand stat overrides generated by Ripples of the Past");
		JsonObject root = new JsonObject();
		root.add("pack", pack);
		return root;
	}

	static JsonObject standStatsTemplate(StandStats stats) {
		return stats.makeConfigTemplate().getAsJsonObject();
	}

	static Path standStatsPath(Path packRoot, ResourceLocation standId) {
		Path normalizedRoot = packRoot.toAbsolutePath().normalize();
		Path statsRoot = normalizedRoot.resolve("data")
				.resolve(standId.getNamespace())
				.resolve(DataDrivenStandsLoader.DIRECTORY)
				.normalize();
		Path standRoot = statsRoot.resolve(standId.getPath()).normalize();
		if (!standRoot.startsWith(statsRoot)) {
			throw new IllegalArgumentException(
					"Stand ID escapes generated data root: " + standId);
		}
		return standRoot.resolve(STATS_FILE);
	}

	static WriteResult writeStandStats(
			Path packRoot, ResourceLocation standId, StandStats stats,
			boolean force)
			throws IOException {
		Path statsPath = standStatsPath(packRoot, standId);
		if (!force && Files.exists(statsPath)) {
			return new WriteResult(statsPath, false);
		}
		writeJsonFile(statsPath, standStatsTemplate(stats));
		return new WriteResult(statsPath, true);
	}

	static record WriteResult(Path path, boolean written) {}

	static void writeJsonFile(Path path, JsonElement json) throws IOException {
		Files.createDirectories(path.getParent());
		Files.writeString(path, GSON.toJson(json), StandardCharsets.UTF_8,
				StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING,
				StandardOpenOption.WRITE);
	}

	private static void writeReadme(Path packRoot) throws IOException {
		Path readmePath = packRoot.resolve("data")
				.resolve(JojoMod.MOD_ID)
				.resolve(DataDrivenStandsLoader.DIRECTORY)
				.resolve("README.txt");
		Files.createDirectories(readmePath.getParent());
		try (InputStream source = JojoConfigCommand.class.getResourceAsStream(
				"/assets/jojo_ripples/texts/stand_stats_readme.txt")) {
			if (source == null) {
				throw new IOException("Missing Stand stat data-pack README resource");
			}
			Files.copy(source, readmePath, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static Component pathComponent(
			CommandSourceStack source, Path path) {
		Path normalized = path.toAbsolutePath().normalize();
		MutableComponent component = Component.literal(normalized.toString())
				.withStyle(ChatFormatting.AQUA);
		if (!source.getServer().isDedicatedServer()) {
			component.withStyle(style -> style
					.withUnderlined(true)
					.withClickEvent(new ClickEvent(
							ClickEvent.Action.OPEN_FILE, normalized.toString()))
					.withHoverEvent(new HoverEvent(
							HoverEvent.Action.SHOW_TEXT,
							Component.translatable(
									"commands.jojoconfig.open_folder"))));
		}
		return component;
	}

	private static CommandSyntaxException generationFailure(Exception exception) {
		JojoMod.getLogger().error("Failed to generate /jojoconfig data pack", exception);
		String message = exception.getMessage();
		return ERROR_GENERATE.create(message != null ? message
				: exception.getClass().getSimpleName());
	}
}
