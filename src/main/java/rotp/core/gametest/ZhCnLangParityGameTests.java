package rotp.core.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 * zh_cn must translate every en_us key; the 1.16 zh_cn values of the Stand removal items
 * and the Hamon / time-stop voice subtitles stay as they were in 1.16.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ZhCnLangParityGameTests {
	private ZhCnLangParityGameTests() {}

	// 1.16 assets/jojo/lang/zh_cn.json values, namespace jojo -> jojo_ripples
	private static final Map<String, String> VALUES_116 = new LinkedHashMap<>();
	static {
		VALUES_116.put("item.jojo_ripples.stand_remover", "『移除』替身");
		VALUES_116.put("item.jojo_ripples.stand_eject", "弹出『替身』");
		VALUES_116.put("item.jojo_ripples.stand_full_clear", "清除所有替身数据");
		VALUES_116.put("item.jojo_ripples.stand_remover_one_time", "『移除』替身（一次性）");
		VALUES_116.put("item.jojo_ripples.stand_eject_one_time", "弹出『替身』 (一次性)");
		VALUES_116.put("item.jojo_ripples.stand_full_clear_one_time", "清除所有替身数据 (一次性)");
		VALUES_116.put("item.jojo_ripples.stand_full_clear.hint", "清除所有替身及觉悟等级并重置觉醒替身需要的等级经验");
		VALUES_116.put("item.jojo_ripples.creative_only_tooltip", "仅限创造");
		VALUES_116.put("jojo_ripples.subtitle.hamon_of_the_sun", "\"太阳的波纹!\"");
		VALUES_116.put("jojo_ripples.subtitle.this_is_sendo", "\"这就是仙道！!\"");
		VALUES_116.put("jojo_ripples.subtitle.this_is_sendo_power", "\"这是仙道的力量!\"");
		VALUES_116.put("jojo_ripples.subtitle.hamon_punch", "\"波纹之拳!\"");
		VALUES_116.put("jojo_ripples.subtitle.hamon_overdrive_beat", "\"波纹疾走的律动!\"");
		VALUES_116.put("jojo_ripples.subtitle.sun_vibration", "\"太阳的震动!\"");
		VALUES_116.put("jojo_ripples.subtitle.hamon_spark", "\"波纹火花!\"");
		VALUES_116.put("jojo_ripples.subtitle.hamon_of_flame", "\"火焰波纹疾走!\"");
		VALUES_116.put("jojo_ripples.subtitle.popow_pow_pow", "啵啵,啵,啵");
		VALUES_116.put("jojo_ripples.subtitle.run_away", "\"你给陆哒呦!!\"");
		VALUES_116.put("jojo_ripples.subtitle.secret_hamon_bubble_launcher", "\"究级奥义：泡沫发射!\"");
		VALUES_116.put("jojo_ripples.subtitle.disc_shaped_hamon_cutter", "\"圆盘形泡沫回旋切割!\"");
		VALUES_116.put("jojo_ripples.subtitle.tomare_toki_yo", "\"时间!停止吧！\"");
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void zhCnTranslatesEveryEnglishKey(GameTestHelper helper) {
		JsonObject en = readLang("en_us");
		JsonObject zh = readLang("zh_cn");
		List<String> missing = new ArrayList<>();
		for (String key : en.keySet()) {
			if (!zh.has(key)) {
				missing.add(key);
			}
		}
		helper.assertTrue(missing.isEmpty(), missing.size() + " en_us keys have no zh_cn entry: " + missing);
		helper.assertTrue(en.size() > 1000, "en_us looks truncated: " + en.size() + " keys");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void zhCnKeeps116RemoverAndSubtitleText(GameTestHelper helper) {
		JsonObject en = readLang("en_us");
		JsonObject zh = readLang("zh_cn");
		for (Map.Entry<String, String> entry : VALUES_116.entrySet()) {
			String key = entry.getKey();
			helper.assertTrue(en.has(key), key + " left en_us; update this test");
			String value = string(zh.get(key));
			helper.assertTrue(entry.getValue().equals(value),
					"zh_cn " + key + " is " + value + ", 1.16 had " + entry.getValue());
		}
		helper.succeed();
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
		InputStream in = ZhCnLangParityGameTests.class.getResourceAsStream("/" + path);
		return in != null ? in : ZhCnLangParityGameTests.class.getClassLoader().getResourceAsStream(path);
	}
}
