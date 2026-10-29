package rotp.addons.thehand;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Walks the live config spec the way NeoForge's ConfigurationScreen names its
 * entries and checks that the Mods-list config screen finds each name in
 * English and Chinese.
 */
@GameTestHolder(TheHandAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TheHandConfigScreenGameTests {
    private TheHandConfigScreenGameTests() {}

    @GameTest(template = "empty", batch = "the_hand_config_screen")
    public static void everyConfigScreenEntryIsNamedInEnglishAndChinese(
            GameTestHelper helper) {
        Set<String> keys = new LinkedHashSet<>();
        keys.add(TheHandAddon.MOD_ID + ".configuration.title");
        String section = TheHandAddon.MOD_ID + ".configuration.section."
                + (TheHandAddon.MOD_ID + "-common.toml")
                        .replaceAll("[^a-zA-Z0-9]+", ".")
                        .toLowerCase(Locale.ENGLISH);
        keys.add(section);
        keys.add(section + ".title");
        collect(TheHandConfig.SPEC, TheHandConfig.SPEC.getSpec(),
                List.of(), keys);
        helper.assertTrue(keys.contains(
                        TheHandAddon.MOD_ID + ".config.percent_damage"),
                "PercentDamage is not named by its translation key: " + keys);
        List<String> missing = new ArrayList<>();
        for (String locale : List.of("en_us", "zh_cn")) {
            JsonObject lang = readLang(locale);
            for (String key : keys) {
                if (!lang.has(key) || lang.get(key).getAsString().isBlank()) {
                    missing.add(locale + ": " + key);
                }
            }
        }
        helper.assertTrue(missing.isEmpty(),
                "config screen entries without a name: " + missing);
        helper.succeed();
    }

    private static void collect(
            ModConfigSpec spec,
            UnmodifiableConfig level,
            List<String> path,
            Set<String> keys) {
        for (UnmodifiableConfig.Entry entry : level.entrySet()) {
            List<String> child = new ArrayList<>(path);
            child.add(entry.getKey());
            String key;
            String comment;
            if (entry.getRawValue() instanceof ModConfigSpec.ValueSpec value) {
                key = value.getTranslationKey();
                comment = value.getComment();
            } else if (entry.getRawValue()
                    instanceof UnmodifiableConfig subsection) {
                key = spec.getLevelTranslationKey(child);
                comment = spec.getLevelComment(child);
                collect(spec, subsection, child, keys);
            } else {
                throw new IllegalStateException(
                        "unexpected config entry " + child);
            }
            if (key == null) {
                key = TheHandAddon.MOD_ID + ".configuration."
                        + entry.getKey();
            }
            keys.add(key);
            if (comment != null && !comment.isBlank()) {
                keys.add(key + ".tooltip");
            }
        }
    }

    private static JsonObject readLang(String locale) {
        Path path = ModList.get()
                .getModFileById(TheHandAddon.MOD_ID)
                .getFile()
                .findResource(
                        "assets", TheHandAddon.MOD_ID, "lang",
                        locale + ".json");
        try {
            return JsonParser.parseString(
                            Files.readString(path, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "cannot read the " + locale + " language file", e);
        }
    }
}
