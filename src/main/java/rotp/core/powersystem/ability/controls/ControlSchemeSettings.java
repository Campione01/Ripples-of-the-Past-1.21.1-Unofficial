package rotp.core.powersystem.ability.controls;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * A player's saved controls customisation for one power type: hotbar order, hidden slots and extra
 * per-ability keys (1.16 HudControlSettings / ControlScheme / ActionKeybindEntry JSON).
 * Kept free of client classes; the client applies it over the power's default template.
 */
public class ControlSchemeSettings {
	public boolean hotbarsEnabled = true;
	public final Map<String, GroupSettings> groups = new LinkedHashMap<>();

	public static class GroupSettings {
		public final List<HotbarLayout> hotbars = new ArrayList<>();
		public final List<Keybind> keybinds = new ArrayList<>();
	}

	public static class HotbarLayout {
		public final List<String> order = new ArrayList<>();
		public final Set<String> hidden = new LinkedHashSet<>();
	}

	public enum OnKeyPress { PERFORM, SELECT }

	public enum KeyActiveType {
		ALWAYS, INSIDE_HUD, OUTSIDE_HUD;

		public boolean canTrigger(boolean isHudActive) {
			return switch (this) {
				case ALWAYS -> true;
				case INSIDE_HUD -> isHudActive;
				case OUTSIDE_HUD -> !isHudActive;
			};
		}
	}

	public static class Keybind {
		public String ability;
		public String key = UNBOUND_KEY;
		public String modifier = "NONE";
		public InputMethod inputMethod = InputMethod.CLICK;
		// 1.16 ActionKeybindEntry defaults
		public OnKeyPress onKeyPress = OnKeyPress.PERFORM;
		public KeyActiveType activeType = KeyActiveType.INSIDE_HUD;
		public boolean visibleInHud = true;
	}

	public static final String UNBOUND_KEY = "key.keyboard.unknown";


	/** Saved order first (names the template no longer has are dropped), then new template slots in template order. */
	public static List<String> mergeOrder(List<String> templateOrder, List<String> savedOrder) {
		Set<String> result = new LinkedHashSet<>();
		for (String key : savedOrder) {
			if (templateOrder.contains(key)) {
				result.add(key);
			}
		}
		result.addAll(templateOrder);
		return new ArrayList<>(result);
	}

	public JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("hotbarsEnabled", hotbarsEnabled);
		JsonObject groupsJson = new JsonObject();
		for (Map.Entry<String, GroupSettings> groupEntry : groups.entrySet()) {
			GroupSettings group = groupEntry.getValue();
			JsonObject groupJson = new JsonObject();
			JsonArray hotbarsJson = new JsonArray();
			for (HotbarLayout hotbar : group.hotbars) {
				JsonObject hotbarJson = new JsonObject();
				hotbarJson.add("order", toArray(hotbar.order));
				hotbarJson.add("hidden", toArray(hotbar.hidden));
				hotbarsJson.add(hotbarJson);
			}
			groupJson.add("hotbars", hotbarsJson);
			JsonArray keybindsJson = new JsonArray();
			for (Keybind keybind : group.keybinds) {
				JsonObject keybindJson = new JsonObject();
				keybindJson.addProperty("ability", keybind.ability);
				keybindJson.addProperty("key", keybind.key);
				keybindJson.addProperty("modifier", keybind.modifier);
				keybindJson.addProperty("input", keybind.inputMethod.name());
				keybindJson.addProperty("onKeyPress", keybind.onKeyPress.name());
				keybindJson.addProperty("active", keybind.activeType.name());
				keybindJson.addProperty("hudIcon", keybind.visibleInHud);
				keybindsJson.add(keybindJson);
			}
			groupJson.add("keybinds", keybindsJson);
			groupsJson.add(groupEntry.getKey(), groupJson);
		}
		json.add("groups", groupsJson);
		return json;
	}

	public static ControlSchemeSettings fromJson(JsonObject json) {
		ControlSchemeSettings settings = new ControlSchemeSettings();
		if (json.has("hotbarsEnabled") && json.get("hotbarsEnabled").isJsonPrimitive()) {
			settings.hotbarsEnabled = json.get("hotbarsEnabled").getAsBoolean();
		}
		JsonObject groupsJson = json.has("groups") && json.get("groups").isJsonObject() ? json.getAsJsonObject("groups") : new JsonObject();
		for (Map.Entry<String, JsonElement> groupEntry : groupsJson.entrySet()) {
			if (!groupEntry.getValue().isJsonObject()) continue;
			JsonObject groupJson = groupEntry.getValue().getAsJsonObject();
			GroupSettings group = new GroupSettings();
			for (JsonElement hotbarElement : array(groupJson, "hotbars")) {
				if (!hotbarElement.isJsonObject()) continue;
				HotbarLayout hotbar = new HotbarLayout();
				JsonObject hotbarJson = hotbarElement.getAsJsonObject();
				for (JsonElement name : array(hotbarJson, "order")) hotbar.order.add(name.getAsString());
				for (JsonElement name : array(hotbarJson, "hidden")) hotbar.hidden.add(name.getAsString());
				group.hotbars.add(hotbar);
			}
			for (JsonElement keybindElement : array(groupJson, "keybinds")) {
				if (!keybindElement.isJsonObject()) continue;
				JsonObject keybindJson = keybindElement.getAsJsonObject();
				String ability = string(keybindJson, "ability", null);
				if (ability == null) continue;
				Keybind keybind = new Keybind();
				keybind.ability = ability;
				keybind.key = string(keybindJson, "key", UNBOUND_KEY);
				keybind.modifier = string(keybindJson, "modifier", "NONE");
				keybind.inputMethod = enumValue(InputMethod.class, string(keybindJson, "input", null), InputMethod.CLICK);
				keybind.onKeyPress = enumValue(OnKeyPress.class, string(keybindJson, "onKeyPress", null), OnKeyPress.PERFORM);
				keybind.activeType = enumValue(KeyActiveType.class, string(keybindJson, "active", null), KeyActiveType.INSIDE_HUD);
				keybind.visibleInHud = !"false".equals(string(keybindJson, "hudIcon", null));
				group.keybinds.add(keybind);
			}
			settings.groups.put(groupEntry.getKey(), group);
		}
		return settings;
	}

	private static JsonArray toArray(Iterable<String> strings) {
		JsonArray array = new JsonArray();
		strings.forEach(array::add);
		return array;
	}

	private static JsonArray array(JsonObject json, String name) {
		JsonElement element = json.get(name);
		return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
	}

	@Nullable
	private static String string(JsonObject json, String name, @Nullable String defaultValue) {
		JsonElement element = json.get(name);
		return element != null && element.isJsonPrimitive() ? element.getAsString() : defaultValue;
	}

	private static <E extends Enum<E>> E enumValue(Class<E> type, @Nullable String name, E defaultValue) {
		if (name != null) {
			for (E value : type.getEnumConstants()) {
				if (value.name().equals(name)) return value;
			}
		}
		return defaultValue;
	}
}
