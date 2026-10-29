package rotp.addons.stand_bearing_creatures.mechanics.combo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

public final class ComboManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().create();

    private static volatile Map<ResourceLocation, PowerComboData>
            standCombos = Map.of();
    private static volatile Map<ResourceLocation, PowerComboData>
            playerPowerCombos = Map.of();

    @Nullable
    public static PowerComboData stand(ResourceLocation standId) {
        return standCombos.get(standId);
    }

    @Nullable
    public static PowerComboData playerPower(ResourceLocation powerId) {
        return playerPowerCombos.get(powerId);
    }

    public static SimpleJsonResourceReloadListener standReloadListener() {
        return new ReloadListener("stand_combos", true);
    }

    public static SimpleJsonResourceReloadListener playerPowerReloadListener() {
        return new ReloadListener("nonstand_combos", false);
    }

    /**
     * 1.16 combo files named actions by registry id ("rotp_zhp:hp_block"); the 1.21 goals look abilities up by
     * their moveset name ("hp_block", see Ability#name()), so a namespaced name would never match. Donor-exact
     * combo files are kept as they are and normalized here instead.
     */
    static String movesetName(@Nullable String action) {
        if (action == null) {
            return null;
        }
        int separator = action.indexOf(':');
        return separator >= 0 ? action.substring(separator + 1) : action;
    }

    private static PowerComboData parseData(JsonObject object) {
        Map<Integer, List<String>> byResolve = new HashMap<>();
        if (object.has("use_by_resolve")) {
            JsonObject resolve =
                    GsonHelper.getAsJsonObject(object, "use_by_resolve");
            resolve.entrySet().forEach(entry -> {
                String digits = entry.getKey().replaceAll("[^0-9]", "");
                if (!digits.isEmpty()) {
                    byResolve.put(
                            Integer.parseInt(digits),
                            GSON.fromJson(
                                    entry.getValue(),
                                    ListStringType.TYPE));
                }
            });
        }

        String block = object.has("block")
                ? movesetName(GsonHelper.getAsString(object, "block"))
                : null;
        MaintenanceStep maintenance = object.has("maintenance")
                ? GSON.fromJson(
                        object.get("maintenance"),
                        MaintenanceStep.class)
                : null;
        if (maintenance != null) {
            maintenance = new MaintenanceStep(
                    movesetName(maintenance.action()),
                    maintenance.condition());
        }

        Map<String, List<ComboStep>> combos = new HashMap<>();
        object.entrySet().forEach(entry -> {
            if (!entry.getKey().equals("use_by_resolve")
                    && !entry.getKey().equals("block")
                    && !entry.getKey().equals("maintenance")
                    && entry.getValue().isJsonArray()) {
                List<ComboStep> steps = GSON.fromJson(
                        entry.getValue(),
                        ComboStepListType.TYPE);
                combos.put(
                        entry.getKey(),
                        steps.stream()
                                .map(ComboStep::withMovesetName)
                                .toList());
            }
        });
        return new PowerComboData(
                Map.copyOf(byResolve),
                block,
                maintenance,
                Map.copyOf(combos));
    }

    private static final class ReloadListener
            extends SimpleJsonResourceReloadListener {
        private final boolean stands;

        private ReloadListener(String directory, boolean stands) {
            super(GSON, directory);
            this.stands = stands;
        }

        @Override
        protected void apply(
                Map<ResourceLocation, JsonElement> resources,
                ResourceManager resourceManager,
                ProfilerFiller profiler) {
            Map<ResourceLocation, PowerComboData> parsed =
                    new HashMap<>();
            for (Map.Entry<ResourceLocation, JsonElement> resource
                    : resources.entrySet()) {
                try {
                    JsonObject root = GsonHelper.convertToJsonObject(
                            resource.getValue(),
                            "combo root");
                    for (Map.Entry<String, JsonElement> power
                            : root.entrySet()) {
                        ResourceLocation powerId =
                                ResourceLocation.tryParse(power.getKey());
                        if (powerId == null
                                || !power.getValue().isJsonObject()) {
                            throw new JsonParseException(
                                    "Invalid power ID or combo object: "
                                            + power.getKey());
                        }
                        parsed.put(
                                powerId,
                                parseData(power.getValue().getAsJsonObject()));
                    }
                }
                catch (RuntimeException exception) {
                    LOGGER.error(
                            "Could not parse mob power combo resource {}",
                            resource.getKey(),
                            exception);
                }
            }
            if (stands) {
                standCombos = Map.copyOf(parsed);
            }
            else {
                playerPowerCombos = Map.copyOf(parsed);
            }
        }
    }

    private static final class ListStringType {
        private static final java.lang.reflect.Type TYPE =
                new com.google.gson.reflect.TypeToken<List<String>>() {}
                        .getType();
    }

    private static final class ComboStepListType {
        private static final java.lang.reflect.Type TYPE =
                new com.google.gson.reflect.TypeToken<List<ComboStep>>() {}
                        .getType();
    }

    private ComboManager() {}
}
