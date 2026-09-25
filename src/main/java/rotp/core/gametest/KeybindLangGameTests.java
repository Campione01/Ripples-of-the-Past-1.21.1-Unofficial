package rotp.core.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import rotp.core.core.JojoMod;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Every key mapping name VanillaKeybinds registers has an en_us and zh_cn name and a ".desc"
 * tooltip (KeyEntryMixin shows it). Names are read from the class file constants, so the
 * client-only class is never loaded on the test server.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KeybindLangGameTests {
	private KeybindLangGameTests() {}

	private static final String KEYBINDS_CLASS = "rotp/core/client/input/VanillaKeybinds.class";
	private static final Pattern KEY_NAME = Pattern.compile(Pattern.quote(JojoMod.MOD_ID + ".key.") + "[a-z0-9_]+");
	// registered without withDescTooltip()
	private static final Set<String> NO_DESC = Set.of(JojoMod.MOD_ID + ".key.jojo_test");
	private static final String MEDITATION = JojoMod.MOD_ID + ".key.meditation";

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void everyCoreKeybindHasNameAndDescInEnAndZh(GameTestHelper helper) {
		Set<String> names = keyNames();
		helper.assertTrue(names.size() >= 10, "only " + names.size() + " key names found in " + KEYBINDS_CLASS + ": " + names);
		helper.assertTrue(names.contains(MEDITATION), MEDITATION + " is no longer registered: " + names);
		JsonObject en = readLang("en_us");
		JsonObject zh = readLang("zh_cn");
		List<String> missing = new ArrayList<>();
		for (String name : names) {
			List<String> keys = NO_DESC.contains(name) ? List.of(name) : List.of(name, name + ".desc");
			for (String key : keys) {
				if (blank(en.get(key))) missing.add("en_us " + key);
				if (blank(zh.get(key))) missing.add("zh_cn " + key);
			}
		}
		helper.assertTrue(missing.isEmpty(), "untranslated key mappings: " + missing);
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void meditationKeyKeeps116Names(GameTestHelper helper) {
		// 1.16 jojo.key.meditation in en_us / zh_cn
		String en = string(readLang("en_us").get(MEDITATION));
		String zh = string(readLang("zh_cn").get(MEDITATION));
		helper.assertTrue("Hamon meditation shortcut".equals(en), "en_us " + MEDITATION + " is " + en);
		helper.assertTrue("波纹冥想".equals(zh), "zh_cn " + MEDITATION + " is " + zh);
		helper.succeed();
	}

	private static Set<String> keyNames() {
		byte[] bytes;
		try (InputStream in = open(KEYBINDS_CLASS)) {
			if (in == null) {
				throw new GameTestAssertException(KEYBINDS_CLASS + " is missing");
			}
			bytes = in.readAllBytes();
		} catch (IOException e) {
			throw new GameTestAssertException("Could not read " + KEYBINDS_CLASS + ": " + e);
		}
		// ASCII constant pool strings appear verbatim in the class file
		Matcher m = KEY_NAME.matcher(new String(bytes, StandardCharsets.ISO_8859_1));
		Set<String> names = new TreeSet<>();
		while (m.find()) {
			names.add(m.group());
		}
		return names;
	}

	private static boolean blank(JsonElement element) {
		String s = string(element);
		return s == null || s.isBlank();
	}

	private static String string(JsonElement element) {
		return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
	}

	private static JsonObject readLang(String locale) {
		String path = "assets/" + JojoMod.MOD_ID + "/lang/" + locale + ".json";
		try (InputStream in = open(path)) {
			if (in == null) {
				throw new GameTestAssertException(path + " is missing");
			}
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (IOException e) {
			throw new GameTestAssertException("Could not read " + path + ": " + e);
		}
	}

	private static InputStream open(String path) {
		InputStream in = KeybindLangGameTests.class.getResourceAsStream("/" + path);
		return in != null ? in : KeybindLangGameTests.class.getClassLoader().getResourceAsStream(path);
	}
}
