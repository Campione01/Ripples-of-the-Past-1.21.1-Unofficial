package rotp.core.gametest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import rotp.core.command.configpack.PlayerStandAssignmentConfig;
import rotp.core.core.JojoMod;
import rotp.core.init.power.ModStands;
import rotp.core.network.s2c.StandAssignmentDataPacket;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.type.StandType;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Either;

import io.netty.buffer.Unpooled;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** 1.16 /jojoconfig assign_stand: per-player Stand lists saved to the world pack and applied to arrow rolls. */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandAssignmentGameTests {
	private StandAssignmentGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void assignStandLimitsArrowRollsAndSavesDataPack(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Path packRoot = server.getWorldPath(LevelResource.DATAPACK_DIR)
				.resolve("jojoconfig").toAbsolutePath().normalize();
		Path metadataPath = packRoot.resolve("pack.mcmeta");
		Path assignPath = PlayerStandAssignmentConfig.filePath(packRoot);
		boolean packExisted = Files.exists(packRoot);
		byte[] oldMetadata = read(metadataPath);
		byte[] oldAssign = read(assignPath);

		String name = "StandAssignTarget";
		ServerPlayer player = FakePlayerFactory.get(helper.getLevel(),
				new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.US_ASCII)), name));
		CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
		CommandSourceStack source = server.createCommandSourceStack()
				.withEntity(player).withLevel(helper.getLevel()).withPermission(4).withSuppressedOutput();
		StandType starPlatinum = ModStands.STAR_PLATINUM.get();
		String spId = starPlatinum.getId().toString();
		try {
			List<StandType> pool = StandUtil.standsForPlayerArrow().toList();
			helper.assertTrue(pool.contains(starPlatinum) && pool.size() > 1
					&& starPlatinum.getStandStats().getRandomWeight() > 0,
					"Precondition: the arrow pool must hold Star Platinum and another Stand");
			PlayerStandAssignmentConfig.clearAssignedStands(player.getGameProfile());

			helper.assertTrue(dispatcher.execute("jojoconfig assign_stand add @s " + spId, source) == 1,
					"assign_stand add did not succeed");
			JsonArray saved = JsonParser.parseString(Files.readString(assignPath)).getAsJsonArray();
			helper.assertTrue(savedEntryHas(saved, player, spId),
					"assign_stand add did not save the player's entry to the jojoconfig pack");

			for (int i = 0; i < 32; i++) {
				Either<StandType, Component> roll = StandUtil.randomStandOrError(player, RandomSource.create(i));
				helper.assertTrue(roll.left().orElse(null) == starPlatinum,
						"An arrow roll gave a Stand outside the player's assignment");
			}

			boolean duplicateFailed = false;
			try {
				dispatcher.execute("jojoconfig assign_stand add @s " + spId, source);
			}
			catch (CommandSyntaxException expected) {
				duplicateFailed = true;
			}
			helper.assertTrue(duplicateFailed, "Assigning the same Stand twice must fail");

			helper.assertTrue(dispatcher.execute("jojoconfig assign_stand remove @s " + spId, source) == 1,
					"assign_stand remove did not succeed");
			helper.assertTrue(PlayerStandAssignmentConfig.getAssignedStands(player) == null,
					"Removing the last assigned Stand must leave the player unrestricted");
			helper.assertTrue(StandUtil.randomStandOrError(player, RandomSource.create(7)).left().isPresent(),
					"An unrestricted player could not roll a Stand");

			// a hand-written entry with an empty list blocks every Stand
			JsonArray edited = PlayerStandAssignmentConfig.toJson();
			JsonObject blocked = new JsonObject();
			blocked.addProperty("uuid", player.getUUID().toString());
			blocked.addProperty("name", name);
			blocked.add("stands", new JsonArray());
			edited.add(blocked);
			PlayerStandAssignmentConfig.loadJson(edited);
			Either<StandType, Component> blockedRoll = StandUtil.randomStandOrError(player, RandomSource.create(3));
			helper.assertTrue(blockedRoll.right().map(Component::getContents).orElse(null)
					instanceof TranslatableContents text && text.getKey().equals("jojo.arrow.assigned_banned"),
					"An empty assignment did not block the arrow with jojo.arrow.assigned_banned");

			helper.assertTrue(dispatcher.execute("jojoconfig assign_stand clear @s", source) == 1,
					"assign_stand clear did not succeed");
			helper.assertTrue(PlayerStandAssignmentConfig.getAssignedStands(player) == null,
					"assign_stand clear left the player's entry");

			// client sync payload keeps both "unrestricted" and a list
			ResourceLocation spLocation = starPlatinum.getId();
			for (Optional<List<ResourceLocation>> value : List.of(
					Optional.<List<ResourceLocation>>empty(), Optional.of(List.of(spLocation)))) {
				RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
				StandAssignmentDataPacket.Handler.STREAM_CODEC.encode(buf, new StandAssignmentDataPacket(value));
				helper.assertTrue(StandAssignmentDataPacket.Handler.STREAM_CODEC.decode(buf).stands().equals(value),
						"Stand assignment packet did not round-trip " + value);
			}
			helper.succeed();
		}
		catch (IOException | CommandSyntaxException exception) {
			throw new AssertionError("/jojoconfig assign_stand failed", exception);
		}
		finally {
			PlayerStandAssignmentConfig.clearAssignedStands(player.getGameProfile());
			restore(packRoot, packExisted, metadataPath, oldMetadata, assignPath, oldAssign);
		}
	}

	private static boolean savedEntryHas(JsonArray saved, ServerPlayer player, String standId) {
		for (JsonElement element : saved) {
			JsonObject entry = element.getAsJsonObject();
			if (entry.has("uuid") && entry.get("uuid").getAsString().equals(player.getUUID().toString())
					&& entry.has("stands")) {
				for (JsonElement stand : entry.getAsJsonArray("stands")) {
					if (stand.getAsString().equals(standId)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static byte[] read(Path path) {
		try {
			return Files.exists(path) ? Files.readAllBytes(path) : null;
		}
		catch (IOException exception) {
			throw new IllegalStateException("Could not snapshot " + path, exception);
		}
	}

	private static void restore(Path packRoot, boolean packExisted, Path metadataPath, byte[] metadata,
			Path assignPath, byte[] assign) {
		try {
			if (!packExisted) {
				if (Files.exists(packRoot)) {
					try (var paths = Files.walk(packRoot)) {
						for (Path path : paths.sorted((left, right) ->
								right.getNameCount() - left.getNameCount()).toList()) {
							Files.deleteIfExists(path);
						}
					}
				}
				return;
			}
			restoreFile(metadataPath, metadata);
			restoreFile(assignPath, assign);
		}
		catch (IOException exception) {
			throw new IllegalStateException("Could not restore GameTest data pack", exception);
		}
	}

	private static void restoreFile(Path path, byte[] content) throws IOException {
		if (content == null) {
			Files.deleteIfExists(path);
		}
		else {
			Files.createDirectories(path.getParent());
			Files.write(path, content);
		}
	}
}
