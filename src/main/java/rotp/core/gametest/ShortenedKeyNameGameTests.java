package rotp.core.gametest;

import java.util.Arrays;
import java.util.Map;

import rotp.core.client.util.functions.ShortenText;
import rotp.core.core.JojoMod;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 ClientUtil.getShortenedTranslationKey: HUD key labels use "<key>.shortened" only when that
 * entry exists, otherwise the full translated key name (never the raw "key.keyboard.*" string).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShortenedKeyNameGameTests {
	private ShortenedKeyNameGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void keyWithoutShortNameShowsFullName(GameTestHelper helper) {
		Language lang = new MapLanguage(Map.of(
				"key.keyboard.tab", "Tab",
				"key.keyboard.escape", "Escape",
				"key.keyboard.escape.shortened", "Esc",
				"key.mouse", "Button %1$s",
				"key.mouse.shortened", "MB%1$s"));

		// no ".shortened" entry: full name, as 1.16
		Component tab = ShortenText.shortenIfAble(lang, Component.translatable("key.keyboard.tab"));
		expect(helper, lang, tab, "key.keyboard.tab", "Tab");
		expect(helper, lang, ShortenText.shortenedTranslatable(lang, "key.keyboard.tab"), "key.keyboard.tab", "Tab");

		// ".shortened" entry present: short name
		Component esc = ShortenText.shortenIfAble(lang, Component.translatable("key.keyboard.escape"));
		expect(helper, lang, esc, "key.keyboard.escape.shortened", "Esc");

		// args survive: extra mouse button 4 -> MB4
		Component mb4 = ShortenText.shortenIfAble(lang, Component.translatable("key.mouse", 4));
		expect(helper, lang, mb4, "key.mouse.shortened", "MB%1$s");
		Object[] args = ((TranslatableContents) mb4.getContents()).getArgs();
		helper.assertTrue(Arrays.equals(args, new Object[] { 4 }), "mouse button args lost: " + Arrays.toString(args));

		// non-translatable names pass through
		Component literal = Component.literal("X");
		helper.assertTrue(ShortenText.shortenIfAble(lang, literal) == literal, "a literal key name was rewritten");

		// global-language overloads use the same rule (this key has no ".shortened" entry anywhere)
		String none = "rotp.gametest.no_short_name";
		expect(helper, null, ShortenText.shortenIfAble(Component.translatable(none)), none, null);
		expect(helper, null, ShortenText.shortenedTranslatable(none), none, null);
		expect(helper, null, ShortenText.shortenedTranslatable(none, 7), none, null);
		helper.succeed();
	}

	// resolves like vanilla TranslatableContents.decompose
	private static void expect(GameTestHelper helper, Language lang, Component name, String key, String text) {
		helper.assertTrue(name.getContents() instanceof TranslatableContents, "not translatable: " + name);
		TranslatableContents contents = (TranslatableContents) name.getContents();
		helper.assertTrue(key.equals(contents.getKey()), "expected key " + key + ", got " + contents.getKey());
		helper.assertTrue(contents.getFallback() == null, "literal fallback '" + contents.getFallback() + "' on " + key);
		if (lang != null) {
			String shown = lang.getOrDefault(contents.getKey());
			helper.assertTrue(text.equals(shown), "label for " + key + " shows '" + shown + "', expected '" + text + "'");
		}
	}

	private static final class MapLanguage extends Language {
		private final Map<String, String> entries;

		MapLanguage(Map<String, String> entries) {
			this.entries = entries;
		}

		@Override
		public String getOrDefault(String key, String defaultValue) {
			return entries.getOrDefault(key, defaultValue);
		}

		@Override
		public boolean has(String key) {
			return entries.containsKey(key);
		}

		@Override
		public boolean isDefaultRightToLeft() {
			return false;
		}

		@Override
		public FormattedCharSequence getVisualOrder(FormattedText text) {
			return FormattedCharSequence.EMPTY;
		}
	}
}
