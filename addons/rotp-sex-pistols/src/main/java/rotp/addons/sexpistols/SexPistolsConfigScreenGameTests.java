package rotp.addons.sexpistols;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
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
import rotp.addons.sexpistols.config.SexPistolsConfig;

/**
 * NeoForge's ConfigurationScreen names the screen, the config file's section, every config level and every value
 * by translation key, and takes a value's tooltip from its ".tooltip" key; a missing key shows raw or in English.
 */
@GameTestHolder(SexPistolsIds.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SexPistolsConfigScreenGameTests {
    private static final List<String> LOCALES = List.of("en_us", "zh_cn");

    private SexPistolsConfigScreenGameTests() {}

    @GameTest(templateNamespace = "rotp_addon_api_smoke", template = "empty", timeoutTicks = 20)
    public static void everyConfigScreenEntryIsNamedInEveryShippedLanguage(GameTestHelper helper) {
        List<ModConfig> configs = ModConfigs.getModConfigs(SexPistolsIds.MOD_ID);
        helper.assertTrue(configs.size() == 1 && configs.get(0).getSpec() == SexPistolsConfig.SPEC,
                "Sex Pistols registers one config file, its common spec: got " + configs.size());
        Set<String> keys = screenKeys(SexPistolsIds.MOD_ID, configs.get(0));
        helper.assertTrue(keys.contains("rotp_sex_pistols.config.sexPistolsHungerExtraTicks"),
                "The config spec walk missed a value: " + keys);
        for (String locale : LOCALES) {
            JsonObject lang = lang(SexPistolsIds.MOD_ID, locale);
            for (String key : keys) {
                helper.assertTrue(lang.has(key) && !lang.get(key).getAsString().isBlank()
                                && !lang.get(key).getAsString().equals(key),
                        locale + " has no text for the config screen key " + key);
            }
        }
        helper.succeed();
    }

    static Set<String> screenKeys(String modId, ModConfig config) {
        ModConfigSpec spec = (ModConfigSpec) config.getSpec();
        Set<String> keys = new LinkedHashSet<>();
        keys.add(modId + ".configuration.title");
        String section = modId + ".configuration.section." + config.getFileName()
                .replaceAll("[^a-zA-Z0-9]+", ".").replaceFirst("^\\.", "").replaceFirst("\\.$", "")
                .toLowerCase(Locale.ENGLISH);
        keys.add(section);
        keys.add(section + ".title");
        collect(modId, spec, spec.getSpec(), List.of(), keys);
        return keys;
    }

    private static void collect(String modId, ModConfigSpec spec, UnmodifiableConfig level, List<String> path,
            Set<String> keys) {
        for (Map.Entry<String, Object> entry : level.valueMap().entrySet()) {
            List<String> entryPath = new ArrayList<>(path);
            entryPath.add(entry.getKey());
            if (entry.getValue() instanceof ModConfigSpec.ValueSpec value) {
                String key = value.getTranslationKey() != null ? value.getTranslationKey()
                        : modId + ".configuration." + entry.getKey();
                keys.add(key);
                if (value.getComment() != null && !value.getComment().isBlank()) {
                    keys.add(key + ".tooltip");
                }
            }
            else if (entry.getValue() instanceof UnmodifiableConfig child) {
                String levelKey = spec.getLevelTranslationKey(entryPath);
                String key = levelKey != null ? levelKey : modId + ".configuration." + entry.getKey();
                keys.add(key);
                String comment = spec.getLevelComment(entryPath);
                if (comment != null && !comment.isBlank()) {
                    keys.add(key + ".tooltip");
                }
                collect(modId, spec, child, entryPath, keys);
            }
        }
    }

    private static JsonObject lang(String modId, String locale) {
        Path path = ModList.get().getModFileById(modId).getFile()
                .findResource("assets", modId, "lang", locale + ".json");
        try {
            return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        catch (IOException e) {
            throw new IllegalStateException("Could not read " + modId + " " + locale, e);
        }
    }
}
