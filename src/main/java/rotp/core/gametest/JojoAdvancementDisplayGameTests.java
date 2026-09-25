package rotp.core.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import rotp.core.core.JojoMod;
import rotp.core.init.ModItems;
import rotp.core.item.StoneMaskItem;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.MapItemColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 had a JoJo advancement tab with its own icons. The port kept 32 icons in the pre-1.20.5 {"item": ...} form,
 * so their display was dropped without a log line and the tab (root display) never existed.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class JojoAdvancementDisplayGameTests {
	private JojoAdvancementDisplayGameTests() {}

	private static final String[] NAMES = {
			"adandon_hamon", "buy_map", "coco_jumbo", "coco_jumbo_key", "coffin_sleep", "create_zombie",
			"cure_vampirism", "destroy_stone_mask", "drain_humans", "get_hamon", "get_pillarman", "get_stand",
			"get_vampirism", "hamon_chicken", "hamon_kill_vampire", "hamon_mastery", "hamon_max_training",
			"hamon_temple", "kill_pillarman", "last_hamon", "mask_suicide", "meteorite", "mr_president",
			"mr_president_walls", "no_stand_kill_stand", "pillarman_evolve", "pillarman_evolve_aja",
			"pillarman_heat_mode", "pillarman_kill_hamon", "pillarman_kill_pillarman", "pillarman_kill_vampire",
			"pillarman_light_mode", "pillarman_temple", "pillarman_wind_mode", "resolve", "root", "soul",
			"stand_arrow", "stand_arrow_shot", "stand_kill_stand", "stand_lost_rps", "stand_max", "stone_mask",
			"summon_stand", "vampire_hamon_damage_scarf", "vampire_kill_hamon" };

	// {Icon:N} on meteoric_scrap in the 1.16 JSONs
	private static final Map<String, Integer> SCRAP_ICONS = new HashMap<>();
	static {
		SCRAP_ICONS.put("last_hamon", 2);
		SCRAP_ICONS.put("vampire_kill_hamon", 3);
		SCRAP_ICONS.put("stand_kill_stand", 4);
		SCRAP_ICONS.put("stand_max", 5);
		SCRAP_ICONS.put("resolve", 6);
		SCRAP_ICONS.put("time_stop_9", 7);
		SCRAP_ICONS.put("get_stand", 8);
		SCRAP_ICONS.put("summon_stand", 9);
		SCRAP_ICONS.put("soul", 10);
		SCRAP_ICONS.put("stand_lost_rps", 11);
		SCRAP_ICONS.put("hamon_max_training", 13);
		SCRAP_ICONS.put("get_pillarman", 14);
		SCRAP_ICONS.put("pillarman_wind_mode", 15);
		SCRAP_ICONS.put("pillarman_heat_mode", 16);
		SCRAP_ICONS.put("pillarman_light_mode", 17);
		SCRAP_ICONS.put("pillarman_kill_pillarman", 18);
		SCRAP_ICONS.put("pillarman_kill_vampire", 19);
		SCRAP_ICONS.put("pillarman_kill_hamon", 20);
		SCRAP_ICONS.put("kill_pillarman", 21);
		SCRAP_ICONS.put("coco_jumbo", 22);
	}

	@GameTest(template = "empty")
	public static void everyJojoAdvancementKeepsItsDisplay(GameTestHelper helper) {
		ServerAdvancementManager advancements = helper.getLevel().getServer().getAdvancements();
		for (String name : NAMES) {
			AdvancementHolder holder = advancements.get(JojoMod.resLoc("jojo/" + name));
			helper.assertTrue(holder != null, "Missing advancement jojo/" + name);
			helper.assertTrue(holder.value().display().isPresent(), "jojo/" + name + " lost its display (icon did not parse)");
		}
		for (AdvancementHolder holder : advancements.getAllAdvancements()) {
			if (holder.id().getNamespace().equals(JojoMod.MOD_ID) && holder.id().getPath().startsWith("jojo/")) {
				helper.assertTrue(holder.value().display().isPresent(), holder.id() + " lost its display");
			}
		}
		AdvancementNode root = advancements.tree().get(JojoMod.resLoc("jojo/root"));
		helper.assertTrue(root != null && root.parent() == null, "jojo/root is not a tab root");
		DisplayInfo rootDisplay = root.advancement().display().orElseThrow();
		helper.assertTrue(rootDisplay.getBackground().isPresent(), "jojo/root has no tab background");
		// 1.16 {CustomModel:1}: the mod logo, drawn by the debug item renderer
		helper.assertTrue(rootDisplay.getIcon().is(ModItems.DEBUG_ITEM.get()), "jojo/root icon is not the mod logo");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void jojoAdvancementIconsMatch116(GameTestHelper helper) {
		ServerAdvancementManager advancements = helper.getLevel().getServer().getAdvancements();
		for (Map.Entry<String, Integer> entry : SCRAP_ICONS.entrySet()) {
			ItemStack icon = icon(helper, advancements, entry.getKey());
			CustomModelData data = icon.get(DataComponents.CUSTOM_MODEL_DATA);
			helper.assertTrue(icon.is(ModItems.METEORIC_SCRAP.get()) && data != null && data.value() == entry.getValue(),
					"jojo/" + entry.getKey() + " icon should be meteoric_scrap custom_model_data " + entry.getValue()
					+ ", was " + icon + " " + data);
		}
		for (String name : new String[] { "get_vampirism", "pillarman_evolve", "mask_suicide", "pillarman_evolve_aja" }) {
			ItemStack icon = icon(helper, advancements, name);
			boolean aja = name.equals("mask_suicide") || name.equals("pillarman_evolve_aja");
			helper.assertTrue(icon.is(aja ? ModItems.AJA_STONE_MASK.get() : ModItems.STONE_MASK.get())
					&& StoneMaskItem.getActivatedTicks(icon) > 0, "jojo/" + name + " icon should be an activated mask");
		}
		helper.assertTrue(icon(helper, advancements, "get_hamon").is(ModItems.BREATH_CONTROL_MASK.get()), "get_hamon icon");
		helper.assertTrue(icon(helper, advancements, "adandon_hamon").is(ModItems.BREATH_CONTROL_MASK.get()), "adandon_hamon icon");
		helper.assertTrue(icon(helper, advancements, "hamon_temple").is(Items.ORANGE_TERRACOTTA), "hamon_temple icon");
		helper.assertTrue(icon(helper, advancements, "pillarman_temple").is(ModItems.SLUMBERING_PILLARMAN.get()), "pillarman_temple icon");
		// 1.16 jojo:meteoric_ore
		helper.assertTrue(icon(helper, advancements, "meteorite").is(ModItems.METEORITE_CORE.get()), "meteorite icon");
		ItemStack map = icon(helper, advancements, "buy_map");
		MapItemColor color = map.get(DataComponents.MAP_COLOR);
		helper.assertTrue(map.is(Items.FILLED_MAP) && color != null && color.rgb() == 7171001, "buy_map icon map color");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void meteoricScrapIconOverridesResolve(GameTestHelper helper) {
		JsonObject model = readJson(helper, "models/item/meteoric_scrap.json");
		Map<Integer, String> overrides = new HashMap<>();
		int previous = 0;
		for (JsonElement element : model.getAsJsonArray("overrides")) {
			JsonObject override = element.getAsJsonObject();
			int value = override.getAsJsonObject("predicate").get("custom_model_data").getAsInt();
			// the last matching override wins, so thresholds must ascend
			helper.assertTrue(value > previous, "meteoric_scrap overrides are not ascending at " + value);
			previous = value;
			overrides.put(value, override.get("model").getAsString());
		}
		for (int i = 1; i <= 22; i++) {
			helper.assertTrue(overrides.containsKey(i), "meteoric_scrap has no custom_model_data " + i + " icon (1.16 Icon:" + i + ")");
		}
		for (AdvancementHolder holder : helper.getLevel().getServer().getAdvancements().getAllAdvancements()) {
			if (!holder.id().getNamespace().equals(JojoMod.MOD_ID)) continue;
			Optional<DisplayInfo> display = holder.value().display();
			if (display.isEmpty() || !display.get().getIcon().is(ModItems.METEORIC_SCRAP.get())) continue;
			CustomModelData data = display.get().getIcon().get(DataComponents.CUSTOM_MODEL_DATA);
			helper.assertTrue(data == null || overrides.containsKey(data.value()), holder.id() + " icon has no model override");
		}
		String prefix = JojoMod.MOD_ID + ":";
		for (String modelId : overrides.values()) {
			helper.assertTrue(modelId.startsWith(prefix), "Foreign icon model " + modelId);
			JsonObject iconModel = readJson(helper, "models/" + modelId.substring(prefix.length()) + ".json");
			for (Map.Entry<String, JsonElement> texture : iconModel.getAsJsonObject("textures").entrySet()) {
				String tex = texture.getValue().getAsString();
				helper.assertTrue(tex.startsWith(prefix + "item/") || tex.startsWith(prefix + "block/") || tex.startsWith(prefix + "entity/"),
						modelId + " texture " + tex + " is outside the block atlas");
				helper.assertTrue(exists("textures/" + tex.substring(prefix.length()) + ".png"), modelId + " texture " + tex + " is missing");
			}
		}
		helper.succeed();
	}

	// 1.16 jojo/meteorite: after buy_map, granted by standing at the jojo:meteorite structure (not by holding scrap)
	@GameTest(template = "empty")
	public static void meteoriteAdvancementNeedsTheStructure(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		AdvancementHolder holder = level.getServer().getAdvancements().get(JojoMod.resLoc("jojo/meteorite"));
		helper.assertTrue(holder != null, "Missing advancement jojo/meteorite");
		helper.assertTrue(holder.value().parent().equals(Optional.of(JojoMod.resLoc("jojo/buy_map"))),
				"jojo/meteorite parent should be jojo/buy_map, was " + holder.value().parent());
		helper.assertTrue(holder.value().criteria().size() == 1, "jojo/meteorite should have one criterion");
		Criterion<?> criterion = holder.value().criteria().values().iterator().next();
		helper.assertTrue(criterion.trigger() == CriteriaTriggers.LOCATION,
				"jojo/meteorite should use minecraft:location, was " + criterion.trigger());
		ContextAwarePredicate predicate = ((SimpleCriterionTrigger.SimpleInstance) criterion.triggerInstance()).player().orElse(null);
		helper.assertTrue(predicate != null, "jojo/meteorite has no player predicate");

		Registry<Structure> structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
		Structure meteorite = structures.get(JojoMod.resLoc("meteorite"));
		Structure temple = structures.get(JojoMod.resLoc("hamon_temple"));
		helper.assertTrue(meteorite != null && temple != null, "meteorite or hamon_temple structure is not registered");
		BlockPos inside = helper.absolutePos(new BlockPos(1, 2, 1));
		FakePlayer player = FakePlayerFactory.get(level,
				new GameProfile(UUID.fromString("5d1a3c2e-8f0b-4e6a-9b7c-2f4e6a8c0d11"), "rotp_meteorite_probe"));
		player.moveTo(inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5, 0, 0);
		// temporary structure starts in this chunk, restored before returning
		LevelChunk chunk = level.getChunkAt(inside);
		Map<Structure, StructureStart> starts = new HashMap<>(chunk.getAllStarts());
		Map<Structure, LongSet> refs = new HashMap<>();
		chunk.getAllReferences().forEach((structure, set) -> refs.put(structure, new LongOpenHashSet(set)));
		try {
			helper.assertTrue(!predicate.matches(EntityPredicate.createContext(player, player)),
					"jojo/meteorite granted outside any structure");
			placeStart(chunk, temple, inside);
			helper.assertTrue(!predicate.matches(EntityPredicate.createContext(player, player)),
					"jojo/meteorite granted inside a Hamon temple");
			placeStart(chunk, meteorite, inside);
			helper.assertTrue(predicate.matches(EntityPredicate.createContext(player, player)),
					"jojo/meteorite not granted inside the meteorite structure");
			player.moveTo(inside.getX() + 0.5, inside.getY() + 10, inside.getZ() + 0.5, 0, 0);
			helper.assertTrue(!predicate.matches(EntityPredicate.createContext(player, player)),
					"jojo/meteorite granted outside the meteorite pieces");
		}
		finally {
			chunk.setAllStarts(starts);
			chunk.setAllReferences(refs);
		}
		helper.succeed();
	}

	// one 3x3x3 stand-in piece around pos, referenced from its own chunk
	private static void placeStart(LevelChunk chunk, Structure structure, BlockPos pos) {
		BoundingBox box = new BoundingBox(pos.getX() - 1, pos.getY() - 1, pos.getZ() - 1, pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
		// never saved: the chunk data is restored in the same tick
		StructurePiece piece = new StructurePiece(StructurePieceType.JIGSAW, 0, box) {
			@Override
			protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {}

			@Override
			public void postProcess(WorldGenLevel level, StructureManager manager, ChunkGenerator generator, RandomSource random,
					BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {}
		};
		ChunkPos chunkPos = new ChunkPos(pos);
		chunk.setStartForStructure(structure, new StructureStart(structure, chunkPos, 0, new PiecesContainer(List.of(piece))));
		chunk.addReferenceForStructure(structure, chunkPos.toLong());
	}

	private static ItemStack icon(GameTestHelper helper, ServerAdvancementManager advancements, String name) {
		AdvancementHolder holder = advancements.get(JojoMod.resLoc("jojo/" + name));
		helper.assertTrue(holder != null && holder.value().display().isPresent(), "jojo/" + name + " has no display");
		return holder.value().display().get().getIcon();
	}

	private static InputStream open(String assetPath) {
		String path = "assets/" + JojoMod.MOD_ID + "/" + assetPath;
		InputStream in = JojoAdvancementDisplayGameTests.class.getResourceAsStream("/" + path);
		return in != null ? in : JojoAdvancementDisplayGameTests.class.getClassLoader().getResourceAsStream(path);
	}

	private static boolean exists(String assetPath) {
		try (InputStream in = open(assetPath)) {
			return in != null;
		} catch (IOException e) {
			return false;
		}
	}

	private static JsonObject readJson(GameTestHelper helper, String assetPath) {
		try (InputStream in = open(assetPath)) {
			helper.assertTrue(in != null, "Missing asset " + assetPath);
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (IOException e) {
			throw new IllegalStateException("Could not read " + assetPath, e);
		}
	}
}
