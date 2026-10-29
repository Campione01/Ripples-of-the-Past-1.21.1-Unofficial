package rotp.addons.stand_bearing_creatures.gametest;

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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.stand_bearing_creatures.MobsWithPowersAddon;

/**
 * NeoForge's ConfigurationScreen names each row by the value's translation
 * key, else {@code <modid>.configuration.<key>}, and each section by its level
 * key, else the same fallback; a missing key shows raw on the screen.
 */
@GameTestHolder(MobsWithPowersAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MobConfigScreenGameTests {
    private static final String NAMESPACE = MobsWithPowersAddon.MOD_ID;
    private static final List<String> LANGUAGES =
            List.of("en_us", "zh_cn");

    private MobConfigScreenGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void configScreenNamesEveryValue(GameTestHelper helper) {
        Set<String> keys = requiredKeys(helper);
        List<String> missing = new ArrayList<>();
        for (String language : LANGUAGES) {
            JsonObject lang = language(language);
            for (String key : keys) {
                JsonElement value = lang.get(key);
                if (value == null || !value.isJsonPrimitive()
                        || value.getAsString().isBlank()) {
                    missing.add(language + ":" + key);
                }
            }
        }
        helper.assertTrue(missing.isEmpty(),
                "config screen keys without a translation: " + missing);
        helper.succeed();
    }

    private static Set<String> requiredKeys(GameTestHelper helper) {
        Set<String> keys = new LinkedHashSet<>();
        keys.add(NAMESPACE + ".configuration.title");
        List<ModConfig> configs =
                ModConfigs.getModConfigs(MobsWithPowersAddon.MOD_ID);
        helper.assertTrue(configs.size() == 1,
                "expected the one registered Stand-Bearing Creatures config, found "
                        + configs.size());
        for (ModConfig config : configs) {
            String section = NAMESPACE + ".configuration.section."
                    + config.getFileName()
                            .replaceAll("[^a-zA-Z0-9]+", ".")
                            .replaceFirst("^\\.", "")
                            .replaceFirst("\\.$", "")
                            .toLowerCase(Locale.ENGLISH);
            keys.add(section);
            keys.add(section + ".title");
            ModConfigSpec spec = (ModConfigSpec) config.getSpec();
            collect(spec, spec.getValues(), spec.getSpec(),
                    List.of(), keys);
        }
        helper.assertTrue(keys.size() == 19,
                "the Stand-Bearing Creatures config screen shows 19 named texts, found "
                        + keys);
        return keys;
    }

    private static void collect(
            ModConfigSpec spec,
            UnmodifiableConfig values,
            UnmodifiableConfig specs,
            List<String> path,
            Set<String> keys) {
        for (UnmodifiableConfig.Entry entry : values.entrySet()) {
            String key = entry.getKey();
            List<String> at = new ArrayList<>(path);
            at.add(key);
            Object raw = entry.getRawValue();
            Object node = specs.getRaw(List.of(key));
            if (raw instanceof ModConfigSpec.ConfigValue<?>
                    && node instanceof ModConfigSpec.ValueSpec value) {
                String name = value.getTranslationKey() != null
                        ? value.getTranslationKey()
                        : NAMESPACE + ".configuration." + key;
                keys.add(name);
                if (hasOwnComment(value.getComment())) {
                    keys.add(name + ".tooltip");
                }
            }
            else if (raw instanceof UnmodifiableConfig section
                    && node instanceof UnmodifiableConfig sectionSpec) {
                String name = spec.getLevelTranslationKey(at);
                keys.add(name != null
                        ? name
                        : NAMESPACE + ".configuration." + key);
                collect(spec, section, sectionSpec, at, keys);
            }
        }
    }

    // A translated tooltip replaces the comment, so only the lines the
    // builder adds itself (default and range) may go untranslated.
    private static boolean hasOwnComment(String comment) {
        if (comment == null) {
            return false;
        }
        for (String line : comment.split("\n")) {
            String text = line.trim();
            if (!text.isEmpty() && !text.startsWith("Default:")
                    && !text.startsWith("Range:")) {
                return true;
            }
        }
        return false;
    }

    private static JsonObject language(String language) {
        String file = "assets/" + NAMESPACE + "/lang/" + language + ".json";
        try {
            Path path = ModList.get()
                    .getModFileById(MobsWithPowersAddon.MOD_ID)
                    .getFile()
                    .findResource(file);
            return JsonParser.parseString(
                            Files.readString(path, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }
        catch (IOException e) {
            throw new IllegalStateException("Could not read " + file, e);
        }
    }
}
