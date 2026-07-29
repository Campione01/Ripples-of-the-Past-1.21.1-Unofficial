package com.github.standobyte.jojo.resource;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class CoreSoundSubtitleResourceSmokeTest {
	private static final String CURRENT_SUBTITLE_PREFIX =
			"jojo_ripples.subtitle.";
	private static final String DONOR_SUBTITLE_PREFIX = "jojo.subtitle.";

	private static final Set<String> MINECRAFT_BASE_SUBTITLES = Set.of(
			"subtitles.entity.player.attack.strong",
			"subtitles.item.bucket.empty",
			"subtitles.item.bucket.fill");

	private static final Map<String, String> V1_16_5_DONOR_SUBTITLES =
			Map.ofEntries(
					Map.entry("jojo.subtitle.disc_shaped_hamon_cutter",
							"\"Disc-shaped Hamon Cutter!\""),
					Map.entry("jojo.subtitle.hamon_of_flame",
							"\"Hamon of Flame!\""),
					Map.entry("jojo.subtitle.hamon_of_the_sun",
							"\"Hamon of the Sun!\""),
					Map.entry("jojo.subtitle.hamon_overdrive_beat",
							"\"Hamon Overdrive Beat!\""),
					Map.entry("jojo.subtitle.hamon_punch",
							"\"Hamon Punch!\""),
					Map.entry("jojo.subtitle.hamon_spark",
							"\"Hamon spark!\""),
					Map.entry("jojo.subtitle.popow_pow_pow",
							"POPOW POW POW"),
					Map.entry("jojo.subtitle.run_away",
							"\"Run away!!\""),
					Map.entry("jojo.subtitle.secret_hamon_bubble_launcher",
							"\"Secret technique: Bubble Launcher!\""),
					Map.entry("jojo.subtitle.sun_vibration",
							"\"Vibration of the Sun!\""),
					Map.entry("jojo.subtitle.this_is_sendo",
							"\"This is Sendo!\""),
					Map.entry("jojo.subtitle.this_is_sendo_power",
							"\"This is Sendo power!\""),
					Map.entry("jojo.subtitle.tomare_toki_yo",
							"\"Stop, time!\""));

	private CoreSoundSubtitleResourceSmokeTest() {}

	public static void main(String[] args) {
		run();
	}

	public static void run() {
		Path assets = Path.of(System.getProperty("user.dir"))
				.resolve("src/main/resources/assets/jojo_ripples");
		JsonObject sounds = readObject(assets.resolve("sounds.json"));
		JsonObject baseEnglish = readObject(assets.resolve("lang/en_us.json"));
		Set<String> coreSubtitleKeys = new TreeSet<>();

		for (Map.Entry<String, JsonElement> sound : sounds.entrySet()) {
			check(sound.getValue().isJsonObject(),
					"core sound definition is not an object: " + sound.getKey());
			JsonElement subtitle = sound.getValue().getAsJsonObject()
					.get("subtitle");
			if (subtitle == null) {
				continue;
			}
			check(isString(subtitle),
					"core sound subtitle is not a string: " + sound.getKey());
			String subtitleKey = subtitle.getAsString();
			if (subtitleKey.startsWith(CURRENT_SUBTITLE_PREFIX)) {
				coreSubtitleKeys.add(subtitleKey);
			}
			else {
				check(MINECRAFT_BASE_SUBTITLES.contains(subtitleKey),
						"unexpected external core subtitle key: " + subtitleKey);
			}
		}

		check(!coreSubtitleKeys.isEmpty(),
				"no core sound subtitle keys were discovered");
		for (String subtitleKey : coreSubtitleKeys) {
			JsonElement translation = baseEnglish.get(subtitleKey);
			check(isString(translation) && !translation.getAsString().isBlank(),
					"core subtitle missing from base en_us: " + subtitleKey);
		}

		V1_16_5_DONOR_SUBTITLES.forEach((donorKey, donorValue) -> {
			String currentKey = CURRENT_SUBTITLE_PREFIX
					+ donorKey.substring(DONOR_SUBTITLE_PREFIX.length());
			check(coreSubtitleKeys.contains(currentKey),
					"migrated donor subtitle is not referenced: " + currentKey);
			check(donorValue.equals(baseEnglish.get(currentKey).getAsString()),
					"migrated donor subtitle text differs: " + currentKey);
		});
	}

	private static JsonObject readObject(Path path) {
		try (Reader reader = Files.newBufferedReader(
				path, StandardCharsets.UTF_8)) {
			JsonElement json = JsonParser.parseReader(reader);
			check(json.isJsonObject(), "resource is not a JSON object: " + path);
			return json.getAsJsonObject();
		}
		catch (IOException exception) {
			throw new AssertionError("failed to read " + path, exception);
		}
	}

	private static boolean isString(JsonElement element) {
		return element != null && element.isJsonPrimitive()
				&& element.getAsJsonPrimitive().isString();
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
