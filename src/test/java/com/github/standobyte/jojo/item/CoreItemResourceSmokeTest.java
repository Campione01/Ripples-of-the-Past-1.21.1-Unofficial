package com.github.standobyte.jojo.item;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class CoreItemResourceSmokeTest {
	private static final Map<String, String> MODEL_FALLBACKS = Map.of(
			"hamon_master_spawn_egg.json",
			"{\"parent\":\"minecraft:item/template_spawn_egg\"}",
			"hungry_zombie_spawn_egg.json",
			"{\"parent\":\"minecraft:item/template_spawn_egg\"}",
			"debug_item.json",
			"{\"parent\":\"jojo_ripples:item/mod_logo\"}",
			"clothes.json",
			"{\"parent\":\"minecraft:item/generated\"}");

	private static final Map<String, String> ITEM_TEXTURE_DONORS = Map.ofEntries(
			Map.entry("block_anchor.png",
					"textures/ability/block_anchor.png"),
			Map.entry("item_icon_awaken.png",
					"textures/power/pillarman.png"),
			Map.entry("item_icon_wind_mode.png",
					"textures/ability/pillarman_divine_sandstorm.png"),
			Map.entry("item_icon_heat_mode.png",
					"textures/ability/pillarman_giant_carthwheel_prison.png"),
			Map.entry("item_icon_light_mode.png",
					"textures/ability/pillarman_blade_dash_attack.png"),
			Map.entry("item_icon_pillarman_punch.png",
					"textures/ability/pillarman_heavy_punch.png"),
			Map.entry("item_icon_vampirism_punch.png",
					"textures/ability/vampirism_claw_lacerate.png"),
			Map.entry("item_icon_hamon_punch.png",
					"textures/ability/hamon_overdrive_beat.png"),
			Map.entry("item_icon_pillarman_explode.png",
					"textures/ability/pillarman_self_detonation.png"));

	private CoreItemResourceSmokeTest() {}

	public static void main(String[] args) {
		run();
	}

	public static void run() {
		Path assets = Path.of(System.getProperty("user.dir"))
				.resolve("src/main/resources/assets/jojo_ripples");
		Path models = assets.resolve("models/item");

		MODEL_FALLBACKS.forEach((name, expected) ->
				check(expected.equals(compact(read(models.resolve(name)))),
						"invalid core item model fallback: " + name));

		ITEM_TEXTURE_DONORS.forEach((aliasName, donorName) -> {
			Path alias = assets.resolve("textures/item").resolve(aliasName);
			Path donor = assets.resolve(donorName);
			check(sameBytes(donor, alias),
					"item-atlas alias differs from donor: " + aliasName);

			String modelName = aliasName.equals("block_anchor.png")
					? "crazy_diamond_non_block_anchor.json"
					: aliasName.replace(".png", ".json");
			String textureId = "jojo_ripples:item/"
					+ aliasName.replace(".png", "");
			String model = compact(read(models.resolve(modelName)));
			check(model.contains("\"layer0\":\"" + textureId + "\""),
					"model does not use item-atlas alias: " + modelName);
		});

		String meteoricScrap = read(models.resolve("meteoric_scrap.json"));
		ITEM_TEXTURE_DONORS.keySet().stream()
				.filter(name -> name.startsWith("item_icon_"))
				.map(name -> name.replace(".png", ""))
				.forEach(model -> check(meteoricScrap.contains(
						"\"jojo_ripples:item/" + model + "\""),
						"meteoric scrap override missing: " + model));
	}

	private static String compact(String json) {
		return json.replaceAll("\\s+", "");
	}

	private static boolean sameBytes(Path first, Path second) {
		try {
			return Files.mismatch(first, second) == -1;
		}
		catch (IOException exception) {
			throw new AssertionError("failed to compare resource alias", exception);
		}
	}

	private static String read(Path path) {
		try {
			return Files.readString(path);
		}
		catch (IOException exception) {
			throw new AssertionError("failed to read " + path, exception);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
