package rotp.core.client.input.controlscheme;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import rotp.core.client.ClientPowerCache;
import rotp.core.client.input.controlscheme.ClientControlScheme.AbilityControlsEntry;
import rotp.core.client.input.controlscheme.ClientControlScheme.Bind;
import rotp.core.client.input.controlscheme.ClientControlScheme.Hotbar;
import rotp.core.client.input.controlscheme.ClientControlScheme.HotbarSlot;
import rotp.core.client.input.controlscheme.ClientControlScheme.MoveGroup;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.PowerType;
import rotp.core.powersystem.ability.controls.ControlSchemeSettings;
import rotp.core.powersystem.ability.controls.ControlSchemeTemplate;
import rotp.core.powersystem.ability.controls.InputMethod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.settings.KeyModifier;

@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT)
public class AllControlSchemes {
	public static Map<ResourceLocation, ClientControlScheme> controls = new HashMap<>();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static ClientControlScheme getForPowerType(PowerType powerType) {
		return controls.get(powerType.getId());
	}

	@SubscribeEvent(priority = EventPriority.LOW)
	public static void createPowerControlSchemes(FMLClientSetupEvent event) {
		for (var playerPowerEntry : JojoRegistries.PLAYER_POWER_TYPES_REG.entrySet()) {
			add(playerPowerEntry.getValue());
		}

		// XXX (data-driven stands) load controls for data-driven stands
		for (var playerPowerEntry : JojoRegistries.DEFAULT_STANDS_REG.entrySet()) {
			add(playerPowerEntry.getValue());
		}
	}

	private static void add(PowerType powerType) {
		ClientControlScheme scheme = createDefault(powerType);
		ControlSchemeSettings settings = load(powerType);
		if (settings != null) {
			applySettings(scheme, settings);
		}
		controls.put(powerType.getId(), scheme);
	}

	private static ClientControlScheme createDefault(PowerType powerType) {
		ControlSchemeTemplate defaultCtrlScheme =
				powerType.makeDefaultControlSchemeTemplate();
		return ClientControlScheme.create(defaultCtrlScheme, powerType);
	}


	// Player customisation (1.16 HudControlSettings: one JSON file per power type)

	public static Path settingsFile(PowerType powerType) {
		ResourceLocation id = powerType.getId();
		return FMLPaths.CONFIGDIR.get().resolve(JojoMod.MOD_ID).resolve("controls")
				.resolve(id.getNamespace()).resolve(id.getPath() + ".json");
	}

	@Nullable
	private static ControlSchemeSettings load(PowerType powerType) {
		Path file = settingsFile(powerType);
		if (!Files.isRegularFile(file)) {
			return null;
		}
		try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			return ControlSchemeSettings.fromJson(JsonParser.parseReader(reader).getAsJsonObject());
		}
		catch (Exception e) {
			JojoMod.getLogger().warn("Could not read controls settings {}", file, e);
			return null;
		}
	}

	public static void save(ClientControlScheme scheme) {
		if (scheme.powerType == null) return;
		Path file = settingsFile(scheme.powerType);
		try {
			Files.createDirectories(file.getParent());
			try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(toSettings(scheme).toJson(), writer);
			}
		}
		catch (IOException e) {
			JojoMod.getLogger().warn("Could not save controls settings {}", file, e);
		}
	}

	/** Deletes the player's changes for the power type and rebuilds its default controls. */
	public static ClientControlScheme reset(PowerType powerType) {
		Path file = settingsFile(powerType);
		try {
			Files.deleteIfExists(file);
		}
		catch (IOException e) {
			JojoMod.getLogger().warn("Could not delete controls settings {}", file, e);
		}
		ClientControlScheme scheme = createDefault(powerType);
		controls.put(powerType.getId(), scheme);
		return scheme;
	}

	static ControlSchemeSettings toSettings(ClientControlScheme scheme) {
		ControlSchemeSettings settings = new ControlSchemeSettings();
		settings.hotbarsEnabled = scheme.hotbarsEnabled;
		for (MoveGroup group : scheme.moveGroups.values()) {
			ControlSchemeSettings.GroupSettings groupSettings = new ControlSchemeSettings.GroupSettings();
			for (Hotbar hotbar : group.hotbars) {
				ControlSchemeSettings.HotbarLayout hotbarLayout = new ControlSchemeSettings.HotbarLayout();
				for (HotbarSlot slot : hotbar.layout) {
					String key = slot.layoutKey();
					if (key == null) continue;
					hotbarLayout.order.add(key);
					if (slot.hidden) {
						hotbarLayout.hidden.add(key);
					}
				}
				groupSettings.hotbars.add(hotbarLayout);
			}
			for (Bind bind : group.binds) {
				if (!bind.custom) continue;
				ControlSchemeSettings.Keybind keybind = new ControlSchemeSettings.Keybind();
				keybind.ability = bind.ability.abilityName();
				ClientKey key = bind.input != null ? bind.input.getKey() : null;
				InputConstants.Key vanillaKey = key != null ? key.getVanillaKey() : null;
				keybind.key = vanillaKey != null ? vanillaKey.getName() : ControlSchemeSettings.UNBOUND_KEY;
				keybind.modifier = bind.input != null ? bind.input.getKeyModifier().name() : KeyModifier.NONE.name();
				keybind.inputMethod = bind.inputMethod;
				keybind.onKeyPress = bind.onKeyPress;
				keybind.activeType = bind.activeType;
				keybind.visibleInHud = bind.visibleInHud;
				groupSettings.keybinds.add(keybind);
			}
			settings.groups.put(group.internalName, groupSettings);
		}
		return settings;
	}

	static void applySettings(ClientControlScheme scheme, ControlSchemeSettings settings) {
		scheme.hotbarsEnabled = settings.hotbarsEnabled;
		for (Map.Entry<String, ControlSchemeSettings.GroupSettings> groupEntry : settings.groups.entrySet()) {
			MoveGroup group = scheme.moveGroups.get(groupEntry.getKey());
			if (group == null) continue;
			ControlSchemeSettings.GroupSettings groupSettings = groupEntry.getValue();
			for (int i = 0; i < group.hotbars.size() && i < groupSettings.hotbars.size(); i++) {
				applyHotbarLayout(group.hotbars.get(i), groupSettings.hotbars.get(i));
			}
			for (ControlSchemeSettings.Keybind keybind : groupSettings.keybinds) {
				Bind bind = new Bind(toInput(keybind.key, keybind.modifier), keybind.inputMethod,
						new AbilityControlsEntry(scheme.powerClassCosmetic, keybind.ability));
				bind.custom = true;
				bind.onKeyPress = keybind.onKeyPress;
				bind.activeType = keybind.activeType;
				bind.visibleInHud = keybind.visibleInHud;
				group.binds.add(bind);
			}
		}
		applyLayouts(scheme);
	}

	/** Refreshes every HUD hotbar from its edited layout. */
	public static void applyLayouts(ClientControlScheme scheme) {
		for (MoveGroup group : scheme.moveGroups.values()) {
			// Hotbars OFF also drops the template LMB/RMB binds (MoveGroup#getBinds)
			group.hotbarsEnabled = scheme.hotbarsEnabled;
			for (Hotbar hotbar : group.hotbars) {
				hotbar.applyLayout(scheme.hotbarsEnabled);
			}
		}
	}

	private static void applyHotbarLayout(Hotbar hotbar, ControlSchemeSettings.HotbarLayout saved) {
		Map<String, HotbarSlot> byKey = new LinkedHashMap<>();
		List<HotbarSlot> unnamed = new ArrayList<>();
		for (HotbarSlot slot : hotbar.layout) {
			String key = slot.layoutKey();
			if (key == null || byKey.putIfAbsent(key, slot) != null) {
				unnamed.add(slot);
			}
		}
		List<String> order = ControlSchemeSettings.mergeOrder(new ArrayList<>(byKey.keySet()), saved.order);
		hotbar.layout.clear();
		for (String key : order) {
			HotbarSlot slot = byKey.get(key);
			slot.hidden = saved.hidden.contains(key);
			hotbar.layout.add(slot);
		}
		hotbar.layout.addAll(unnamed);
	}

	/** A new unbound player key for the ability, using the input method its template controls use. */
	public static Bind addCustomBind(ClientControlScheme scheme, MoveGroup group, String abilityName) {
		Bind bind = new Bind(new ClientInputBind((ClientKey) null), templateInputMethod(group, abilityName),
				new AbilityControlsEntry(scheme.powerClassCosmetic, abilityName));
		bind.custom = true;
		group.binds.add(bind);
		return bind;
	}

	private static InputMethod templateInputMethod(MoveGroup group, String abilityName) {
		for (Hotbar hotbar : group.hotbars) {
			for (HotbarSlot slot : hotbar.layout) {
				for (var byModifier : slot.binds.movesByModifier.values()) {
					for (var byInputMethod : byModifier.entrySet()) {
						for (AbilityControlsEntry entry : byInputMethod.getValue()) {
							if (entry.abilityName().equals(abilityName)) return byInputMethod.getKey();
						}
					}
				}
			}
		}
		for (Bind bind : group.binds) {
			if (!bind.custom && bind.ability.abilityName().equals(abilityName)) return bind.inputMethod;
		}
		return InputMethod.CLICK;
	}

	public static ClientInputBind toInput(String keyName, String modifierName) {
		InputConstants.Key key;
		try {
			key = InputConstants.getKey(keyName);
		}
		catch (RuntimeException e) {
			key = InputConstants.UNKNOWN;
		}
		KeyModifier modifier;
		try {
			modifier = KeyModifier.valueOf(modifierName);
		}
		catch (RuntimeException e) {
			modifier = KeyModifier.NONE;
		}
		return toInput(key, modifier);
	}

	public static ClientInputBind toInput(@Nullable InputConstants.Key key, KeyModifier modifier) {
		if (key == null || key.equals(InputConstants.UNKNOWN)) {
			return new ClientInputBind((ClientKey) null);
		}
		return new ClientInputBind(ClientKey.make(key.getType(), key.getValue()), modifier);
	}

	/**
	 * 1.16 custom keys with KeyActiveType ALWAYS / OUTSIDE_HUD also work while another power's HUD, or none, is open.
	 * The open HUD keeps a key it has its own custom bind on.
	 */
	@Nullable
	public static ClientControlScheme getCustomKeyScheme(ClientKey key, KeyModifier keyModifier, @Nullable ClientControlScheme activeScheme) {
		if (activeScheme != null && activeScheme.hasCustomBindFor(key, keyModifier, true)) {
			return activeScheme;
		}
		for (PowerClass<?> powerClass : PowerClass.values()) {
			Power<?> power = ClientPowerCache.getPower(powerClass);
			if (power != null && power.hasPower()) {
				ClientControlScheme scheme = getForPowerType(power.getPowerType());
				if (scheme != null && scheme != activeScheme && scheme.hasCustomBindFor(key, keyModifier, false)) {
					return scheme;
				}
			}
		}
		return null;
	}

}
