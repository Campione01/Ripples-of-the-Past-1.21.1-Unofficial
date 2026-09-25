package rotp.core.command.configpack;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.core.JojoMod;
import rotp.core.network.s2c.StandAssignmentDataPacket;
import rotp.core.powersystem.standpower.type.StandType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 1.16 PlayerStandAssignmentConfig: per-player Stand lists that limit what a Stand Arrow can give.
 * Stored as data/jojo_ripples/stand_assign/stand_assign.json in a data pack (the /jojoconfig pack):
 * an array of {"uuid", "name", "stands": [ids]}. No entry = unrestricted; an empty "stands" array blocks every Stand.
 */
@EventBusSubscriber(modid = JojoMod.MOD_ID)
public final class PlayerStandAssignmentConfig {
	public static final String RESOURCE_NAME = "stand_assign";
	public static final ResourceLocation FILE_ID = JojoMod.resLoc(RESOURCE_NAME);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private static final List<Entry> serverEntries = new ArrayList<>();
	// the local player's entry, synced by StandAssignmentDataPacket
	@Nullable
	private static List<ResourceLocation> clientEntry;

	private PlayerStandAssignmentConfig() {}

	private static final class Entry {
		@Nullable
		private final UUID uuid;
		private String name;
		@Nullable
		private List<ResourceLocation> stands;

		private Entry(@Nullable UUID uuid, String name, @Nullable List<ResourceLocation> stands) {
			this.uuid = uuid;
			this.name = name;
			this.stands = stands;
		}

		private boolean matches(GameProfile profile) {
			if (uuid != null) {
				return uuid.equals(profile.getId());
			}
			// hand-written entries may name the player only
			return name != null && name.equalsIgnoreCase(profile.getName());
		}
	}

	/** Relative path of the file inside a data pack root. */
	public static Path filePath(Path packRoot) {
		return packRoot.resolve("data").resolve(FILE_ID.getNamespace())
				.resolve(RESOURCE_NAME).resolve(FILE_ID.getPath() + ".json");
	}

	@Nullable
	private static Entry find(GameProfile profile) {
		for (Entry entry : serverEntries) {
			if (entry.matches(profile)) {
				return entry;
			}
		}
		return null;
	}

	/**
	 * @return the Stand ids assigned to the player, or null when the player isn't restricted.
	 * On the client only the local player's entry is known.
	 */
	@Nullable
	public static List<ResourceLocation> getAssignedStands(Player player) {
		if (!player.level().isClientSide()) {
			Entry entry = find(player.getGameProfile());
			return entry != null ? entry.stands : null;
		}
		return player.isLocalPlayer() ? clientEntry : null;
	}

	public static List<StandType> limitToAssignedStands(Player player, List<StandType> availableStands) {
		List<ResourceLocation> assigned = getAssignedStands(player);
		if (assigned == null) {
			return availableStands;
		}
		return availableStands.stream().filter(stand -> assigned.contains(stand.getId())).toList();
	}

	public static boolean addAssignedStand(GameProfile profile, ResourceLocation standId) {
		Entry entry = find(profile);
		if (entry == null) {
			entry = new Entry(profile.getId(), profile.getName(), null);
			serverEntries.add(entry);
		}
		entry.name = profile.getName();
		if (entry.stands == null) {
			entry.stands = new ArrayList<>();
		}
		if (entry.stands.contains(standId)) {
			return false;
		}
		entry.stands.add(standId);
		return true;
	}

	// as in 1.16, removing the last Stand drops the entry (the player is unrestricted again)
	public static boolean removeAssignedStand(GameProfile profile, ResourceLocation standId) {
		Entry entry = find(profile);
		if (entry == null || entry.stands == null || !entry.stands.remove(standId)) {
			return false;
		}
		if (entry.stands.isEmpty()) {
			serverEntries.remove(entry);
		}
		return true;
	}

	public static boolean clearAssignedStands(GameProfile profile) {
		return serverEntries.removeIf(entry -> entry.matches(profile));
	}

	public static void clearAll() {
		serverEntries.clear();
	}

	public static JsonArray toJson() {
		JsonArray array = new JsonArray();
		for (Entry entry : serverEntries) {
			JsonObject json = new JsonObject();
			if (entry.uuid != null) {
				json.addProperty("uuid", entry.uuid.toString());
			}
			if (entry.name != null) {
				json.addProperty("name", entry.name);
			}
			if (entry.stands != null) {
				JsonArray stands = new JsonArray();
				entry.stands.forEach(id -> stands.add(id.toString()));
				json.add("stands", stands);
			}
			array.add(json);
		}
		return array;
	}

	/** Replaces the server entries with the parsed file; null or malformed input clears them. */
	public static void loadJson(@Nullable JsonElement json) {
		serverEntries.clear();
		if (json == null || !json.isJsonArray()) {
			return;
		}
		for (JsonElement element : json.getAsJsonArray()) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject object = element.getAsJsonObject();
			UUID uuid = null;
			if (object.has("uuid")) {
				try {
					uuid = UUID.fromString(object.get("uuid").getAsString());
				}
				catch (RuntimeException invalidUuid) {
					continue;
				}
			}
			String name = object.has("name") ? object.get("name").getAsString() : null;
			if (uuid == null && name == null) {
				continue;
			}
			List<ResourceLocation> stands = null;
			if (object.has("stands") && object.get("stands").isJsonArray()) {
				stands = new ArrayList<>();
				for (JsonElement standJson : object.getAsJsonArray("stands")) {
					ResourceLocation id = ResourceLocation.tryParse(standJson.getAsString());
					if (id != null && !stands.contains(id)) {
						stands.add(id);
					}
				}
			}
			serverEntries.add(new Entry(uuid, name, stands));
		}
	}

	public static void syncToClient(ServerPlayer player) {
		List<ResourceLocation> stands = getAssignedStands(player);
		PacketDistributor.sendToPlayer(player, new StandAssignmentDataPacket(
				Optional.ofNullable(stands).map(List::copyOf)));
	}

	public static void syncToClients(Collection<ServerPlayer> players) {
		players.forEach(PlayerStandAssignmentConfig::syncToClient);
	}

	public static void handleClientPacket(Optional<List<ResourceLocation>> stands) {
		clientEntry = stands.orElse(null);
	}

	private static final class Loader extends SimpleJsonResourceReloadListener {
		private Loader() {
			super(GSON, RESOURCE_NAME);
		}

		@Override
		protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager,
				ProfilerFiller profiler) {
			loadJson(files.get(FILE_ID));
		}
	}

	@SubscribeEvent
	public static void addReloadListener(AddReloadListenerEvent event) {
		event.addListener(new Loader());
	}

	@SubscribeEvent
	public static void syncOnDatapackSync(OnDatapackSyncEvent event) {
		event.getRelevantPlayers().forEach(PlayerStandAssignmentConfig::syncToClient);
	}
}
