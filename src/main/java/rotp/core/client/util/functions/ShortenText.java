package rotp.core.client.util.functions;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;

public class ShortenText {

	public static MutableComponent shortenedTranslatable(String translatableKey) {
		return shortenedTranslatable(Language.getInstance(), translatableKey);
	}

	public static MutableComponent shortenedTranslatable(String translatableKey, Object... args) {
		return shortenedTranslatable(Language.getInstance(), translatableKey, args);
	}

	// 1.16 ClientUtil.getShortenedTranslationKey: "<key>.shortened" only if it exists, else the full translated name
	public static MutableComponent shortenedTranslatable(Language language, String translatableKey, Object... args) {
		String shortenedKey = translatableKey + ".shortened";
		return Component.translatable(language.has(shortenedKey) ? shortenedKey : translatableKey, args);
	}

	public static Component shortenIfAble(Component translatable) {
		return shortenIfAble(Language.getInstance(), translatable);
	}

	public static Component shortenIfAble(Language language, Component translatable) {
		if (translatable.getContents() instanceof TranslatableContents contents) {
			return shortenedTranslatable(language, contents.getKey(), contents.getArgs());
		}
		return translatable;
	}
}
