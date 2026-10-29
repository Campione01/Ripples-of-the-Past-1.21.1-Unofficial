package rotp.addons.cream;

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

/**
 * NeoForge's ConfigurationScreen names each row by the value's translation
 * key, else {@code <modid>.configuration.<key>}, and each section by its level
 * key, else the same fallback; a missing key shows raw on the screen. A row
 * with a range or a comment shows its {@code .tooltip} key, else the spec
 * comment in English.
 */
@GameTestHolder(CreamAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CreamConfigScreenGameTests {
    private static final String NAMESPACE = CreamAddon.MOD_ID;
    private static final List<String> LANGUAGES =
            List.of("en_us", "zh_cn");

    private CreamConfigScreenGameTests() {}

    @GameTest(
            templateNamespace = CreamAddon.MOD_ID,
            template = "empty",
            timeoutTicks = 20)
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

        // NeoForge's list editor offers its "+" button only when the list
        // has a new-element supplier.
        Set<String> lists = new LinkedHashSet<>();
        List<String> withoutAddButton = new ArrayList<>();
        for (ModConfig config : ModConfigs.getModConfigs(CreamAddon.MOD_ID)) {
            listValues(((ModConfigSpec) config.getSpec()).getSpec(), "",
                    lists, withoutAddButton);
        }
        helper.assertTrue(lists.equals(
                        Set.of("void.blockBlacklist", "void.entityBlacklist")),
                "Cream list values changed: " + lists);
        helper.assertTrue(withoutAddButton.isEmpty(),
                "list values without a new-element supplier, their editor "
                        + "has no \"+\" button: " + withoutAddButton);
        helper.succeed();
    }

    private static void listValues(
            UnmodifiableConfig specs,
            String path,
            Set<String> lists,
            List<String> withoutAddButton) {
        for (UnmodifiableConfig.Entry entry : specs.entrySet()) {
            String at = path.isEmpty()
                    ? entry.getKey()
                    : path + "." + entry.getKey();
            Object node = entry.getRawValue();
            if (node instanceof ModConfigSpec.ListValueSpec list) {
                lists.add(at);
                if (list.getNewElementSupplier() == null) {
                    withoutAddButton.add(at);
                }
            }
            else if (node instanceof UnmodifiableConfig section) {
                listValues(section, at, lists, withoutAddButton);
            }
        }
    }

    private static Set<String> requiredKeys(GameTestHelper helper) {
        Set<String> keys = new LinkedHashSet<>();
        keys.add(NAMESPACE + ".configuration.title");
        List<ModConfig> configs =
                ModConfigs.getModConfigs(CreamAddon.MOD_ID);
        helper.assertTrue(configs.size() == 1,
                "expected the one registered Cream config, found "
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
        helper.assertTrue(keys.size() == 75,
                "the Cream config screen shows 75 texts (47 names, "
                        + "28 tooltips), found " + keys);
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
                if (value.getRange() != null
                        || hasComment(value.getComment())) {
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

    // The builder's own "Default:" and "Range:" lines count: untranslated,
    // they show in English in every language.
    private static boolean hasComment(String comment) {
        return comment != null && !comment.isBlank();
    }

    private static JsonObject language(String language) {
        String file = "assets/" + NAMESPACE + "/lang/" + language + ".json";
        try {
            Path path = ModList.get()
                    .getModFileById(CreamAddon.MOD_ID)
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
