package rotp.core.resource;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * jojo_ripples.subtitle.stand_damage_block is the subtitle of a Stand guard (and Silver Chariot's rapier) blocking a
 * hit, "Damage blocked" in en_us. The 1.16 Chinese files had "break blocks" for it; both now read "damage blocked".
 */
public final class StandDamageBlockSubtitleSmokeTest {
	private static final String KEY = "jojo_ripples.subtitle.stand_damage_block";
	private static final String ZH_CN = "伤害被格挡";
	private static final String ZH_TW = "傷害被格擋";
	// "block" as in a cube of the world
	private static final String CUBE_CN = "方块";
	private static final String CUBE_TW = "方塊";

	private StandDamageBlockSubtitleSmokeTest() {}

	public static void run() {
		Path assets = Path.of(System.getProperty("user.dir"))
				.resolve("src/main/resources/assets/jojo_ripples");
		JsonElement sound = readObject(assets.resolve("sounds.json")).get("stand_damage_block");
		check(sound != null && sound.isJsonObject()
				&& KEY.equals(string(sound.getAsJsonObject().get("subtitle"))),
				"stand_damage_block no longer uses " + KEY);

		Path lang = assets.resolve("lang");
		check(ZH_CN.equals(string(readObject(lang.resolve("zh_cn.json")).get(KEY))),
				"zh_cn " + KEY + " must say the damage was blocked");
		check(ZH_TW.equals(string(readObject(lang.resolve("zh_tw.json")).get(KEY))),
				"zh_tw " + KEY + " must say the damage was blocked");
		try (Stream<Path> files = Files.list(lang)) {
			files.filter(file -> file.toString().endsWith(".json")).forEach(file -> {
				String value = string(readObject(file).get(KEY));
				check(value == null || !(value.contains(CUBE_CN) || value.contains(CUBE_TW)),
						file.getFileName() + " translates " + KEY + " as breaking blocks");
			});
		}
		catch (IOException error) {
			throw new AssertionError("failed to list " + lang, error);
		}
	}

	private static String string(JsonElement element) {
		return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
				? element.getAsString() : null;
	}

	private static JsonObject readObject(Path path) {
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonElement json = JsonParser.parseReader(reader);
			check(json.isJsonObject(), "resource is not a JSON object: " + path);
			return json.getAsJsonObject();
		}
		catch (IOException error) {
			throw new AssertionError("failed to read " + path, error);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
