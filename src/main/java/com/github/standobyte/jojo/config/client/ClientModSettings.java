package com.github.standobyte.jojo.config.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.util.Map.Entry;
import java.util.function.Consumer;

import org.slf4j.Logger;

import com.github.standobyte.jojo.util.functions.FileSystemUtil;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;

public class ClientModSettings {

	public static class Settings {
		public float standStatsTranslucency = 0.75F;
		public boolean standStatsInvertBnW = false;
//		public ChooseLifeformScreen.ViewMode viewModeGE = null;
//
//		public PositionConfig barsPosition = PositionConfig.TOP_LEFT;
//		public PositionConfig hotbarsPosition = PositionConfig.TOP_LEFT;
//		public HudTextRender hudTextRender = HudTextRender.FADE_OUT;
//		public boolean hudHotbarFold = true;
		public boolean showLockedSlots = false;
		public boolean abilitySelectionWheel = true;

		public boolean resolveShaders = true;
		public boolean timeStopAnimation = true;
		public boolean standMotionTilt = true;
		public boolean poseOnLmbRmb = true;
		public boolean standAimMarker = false;
		public float standTransparency = 50.0F;
		public float standOthersTransparency = 50.0F;
		public boolean classicStandObstruction = true;
		public boolean standOutline = true;
		public StandAuraSettings standAura = new StandAuraSettings();

		public boolean menacingParticles = true;
		public boolean characterVoiceLines = true;
		public boolean toggleLmbHotbar = false;
		public boolean toggleRmbHotbar = false;
		public boolean toggleDisableHotbars = false;

		public boolean thirdPersonHamonAura = true;
		public boolean firstPersonHamonAura = true;
		public boolean hamonAuraBlur = false;

		public final PlayerClientBroadcastedSettings broadcasted = new PlayerClientBroadcastedSettings();

		private void sanitize() {
			standTransparency = clampTransparency(standTransparency);
			standOthersTransparency = clampTransparency(standOthersTransparency);
			if (standAura == null) {
				standAura = new StandAuraSettings();
			}
			standAura.sanitize();
		}

		private static float clampTransparency(float transparency) {
			if (!Float.isFinite(transparency)) {
				return 50.0F;
			}
			return Math.max(0.0F, Math.min(100.0F, transparency));
		}
	}



	public static void edit(Consumer<Settings> edit, boolean broadcast) {
		getInstance().editSettings(edit, broadcast);
	}

	@Deprecated
	public void editSettings(Consumer<Settings> edit) {
		editSettings(edit, false);
	}

	public void editSettings(Consumer<Settings> edit, boolean broadcast) {
		edit.accept(settings);
		settings.sanitize();
		if (broadcast) {
			settings.broadcasted.broadcastToServer();
		}
		save();
	}

	public static Settings getSettingsReadOnly() {
		return getInstance().settings;
	}



	private static final Logger LOGGER = LogUtils.getLogger();
	public void load() {
		File path = optionsFile;
		if (!path.exists()) {
			LOGGER.info("Client settings file is missing; writing defaults to {}", path);
			save();
			return;
		}
		if (path.length() == 0) {
			LOGGER.warn("Client settings file is empty; recreating defaults at {}", path);
			backupInvalidSettings(path, "empty file");
			save();
			return;
		}

		try (BufferedReader reader = Files.newReader(path, Charsets.UTF_8)) {
			JsonElement element = gson.fromJson(reader, JsonElement.class);
			if (element == null || !element.isJsonObject()) {
				backupInvalidSettings(path, "not a JSON object");
				save();
				return;
			}
			Settings deserialized = gson.fromJson(mergeSettingsWithDefaults(element.getAsJsonObject()), settings.getClass());
			if (deserialized != null) {
				deserialized.sanitize();
				this.settings = deserialized;
			}
		}
		catch (Exception exception) {
			backupInvalidSettings(path, exception.getClass().getSimpleName());
			LOGGER.error("Failed to load mod client settings", (Throwable) exception);
			save();
		}
	}

	private JsonObject mergeSettingsWithDefaults(JsonObject loadedSettings) {
		JsonObject mergedSettings = gson.toJsonTree(new Settings()).getAsJsonObject();
		mergeJsonObjects(mergedSettings, normalizeStandDisplaySettings(migrateLegacyStandDisplaySettings(loadedSettings)));
		return mergedSettings;
	}

	private JsonObject migrateLegacyStandDisplaySettings(JsonObject loaded) {
		JsonObject migrated = loaded.deepCopy();
		migrateInversePercentage(migrated, "standOpacity", "standTransparency");
		migrateInversePercentage(migrated, "standOthersOpacity", "standOthersTransparency");
		if (!migrated.has("classicStandObstruction") && migrated.has("standObstructionMode")) {
			Boolean classic = readLegacyClassicStandObstruction(migrated.get("standObstructionMode"));
			if (classic != null) {
				migrated.addProperty("classicStandObstruction", classic);
			}
		}
		migrated.remove("standObstructionMode");
		migrated.remove("standOpacity");
		migrated.remove("standOthersOpacity");
		return migrated;
	}

	private JsonObject normalizeStandDisplaySettings(JsonObject settings) {
		normalizeTransparencyField(settings, "standTransparency");
		normalizeTransparencyField(settings, "standOthersTransparency");
		normalizeBooleanField(settings, "classicStandObstruction");
		normalizeBooleanField(settings, "standOutline");
		return settings;
	}

	private void migrateInversePercentage(JsonObject migrated, String legacyKey, String newKey) {
		if (migrated.has(newKey) || !migrated.has(legacyKey)) {
			return;
		}
		Float legacyValue = readFiniteFloat(migrated.get(legacyKey));
		if (legacyValue != null) {
			migrated.addProperty(newKey, 100.0F - clampPercentage(legacyValue));
		}
	}

	private Float readFiniteFloat(JsonElement value) {
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
			return null;
		}
		float number = value.getAsFloat();
		return Float.isFinite(number) ? number : null;
	}

	private Boolean readBoolean(JsonElement value) {
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
			return null;
		}
		return value.getAsBoolean();
	}

	private Boolean readLegacyClassicStandObstruction(JsonElement value) {
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
			return null;
		}
		String mode = value.getAsString();
		if ("CLASSIC_1_16_5".equals(mode)) {
			return true;
		}
		if ("OFFICIAL_1_21_1".equals(mode)) {
			return false;
		}
		return null;
	}

	private float clampPercentage(float value) {
		return Math.max(0.0F, Math.min(100.0F, value));
	}

	private void normalizeTransparencyField(JsonObject settings, String key) {
		if (!settings.has(key)) {
			return;
		}
		Float value = readFiniteFloat(settings.get(key));
		if (value == null) {
			settings.remove(key);
			return;
		}
		settings.addProperty(key, clampPercentage(value));
	}

	private void normalizeBooleanField(JsonObject settings, String key) {
		if (!settings.has(key)) {
			return;
		}
		Boolean value = readBoolean(settings.get(key));
		if (value == null) {
			settings.remove(key);
			return;
		}
		settings.addProperty(key, value);
	}

	private static void mergeJsonObjects(JsonObject target, JsonObject source) {
		for (Entry<String, JsonElement> entry : source.entrySet()) {
			JsonElement current = target.get(entry.getKey());
			JsonElement incoming = entry.getValue();
			if (current != null && current.isJsonObject() && incoming != null && incoming.isJsonObject()) {
				mergeJsonObjects(current.getAsJsonObject(), incoming.getAsJsonObject());
			}
			else {
				target.add(entry.getKey(), incoming);
			}
		}
	}

	private void backupInvalidSettings(File path, String reason) {
		try {
			if (path.exists()) {
				File parent = path.getParentFile() != null ? path.getParentFile() : path.getAbsoluteFile().getParentFile();
				File backup = new File(parent, path.getName() + ".invalid-" + System.currentTimeMillis() + ".bak");
				Files.copy(path, backup);
				LOGGER.warn("Backed up invalid client settings ({}) to {}", reason, backup);
			}
		}
		catch (Exception exception) {
			LOGGER.error("Failed to back up invalid client settings", (Throwable) exception);
		}
	}

	public void save() {
		File tempFile = null;
		try {
			settings.sanitize();
			File parent = optionsFile.getAbsoluteFile().getParentFile();
			if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
				throw new IOException("Failed to create settings directory: " + parent);
			}
			tempFile = File.createTempFile(optionsFile.getName(), ".tmp", parent);
			try (BufferedWriter writer = FileSystemUtil.newWriterMkDir(tempFile, Charsets.UTF_8)) {
				gson.toJson(settings, writer);
			}
			replaceSettingsFile(tempFile, optionsFile);
			tempFile = null;
		}
		catch (Exception exception) {
			LOGGER.error("Failed to save mod client settings", (Throwable) exception);
		}
		finally {
			if (tempFile != null && tempFile.exists() && !tempFile.delete()) {
				LOGGER.warn("Failed to delete temporary client settings file {}", tempFile);
			}
		}
	}

	private void replaceSettingsFile(File source, File destination) throws IOException {
		try {
			java.nio.file.Files.move(source.toPath(), destination.toPath(),
					StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		}
		catch (AtomicMoveNotSupportedException exception) {
			java.nio.file.Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
	}



	private static ClientModSettings instance;
	private final File optionsFile;
	private final Gson gson;
	private Settings settings = new Settings();

	public static void init(File optionsFile) {
		if (instance == null || !sameOptionsFile(instance.optionsFile, optionsFile)) {
			instance = new ClientModSettings(optionsFile);
		}
	}

	private static boolean sameOptionsFile(File first, File second) {
		try {
			return first.getCanonicalFile().equals(second.getCanonicalFile());
		}
		catch (IOException exception) {
			return first.getAbsoluteFile().equals(second.getAbsoluteFile());
		}
	}

	private ClientModSettings(File optionsFile) {
		this.optionsFile = optionsFile;
		this.gson = new GsonBuilder().setPrettyPrinting().create();
		load();
	}

	public static ClientModSettings getInstance() {
		return instance;
	}
}
