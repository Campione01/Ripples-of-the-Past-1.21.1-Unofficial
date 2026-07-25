package com.github.standobyte.jojo.config.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ClientModSettingsSmokeTest {
	private static final GsonReflectionJson GSON = new GsonReflectionJson();

	private ClientModSettingsSmokeTest() {
	}

	public static void main(String[] args) throws Exception {
		try {
			loadKeepsDefaultSettingsWhenFileIsEmpty();
			loadMigratesOfficialLegacyDisplaySettings();
			loadMigratesClassicLegacyDisplaySettings();
			loadClampsOutOfRangeLegacyOpacityMigration();
			loadPrefersNewStandDisplayKeysOverLegacyValues();
			loadInvalidNewStandDisplayKeysDoNotFallBackToLegacyValues();
			loadClampsFiniteTransparencyValues();
			loadDefaultsNonFiniteStandDisplayFieldsWithoutTouchingValidFields();
			loadDefaultsInvalidClassicBooleanWithoutTouchingValidFields();
			loadInvalidStandDisplayFieldsPreserveUnrelatedSettings();
			loadUnknownLegacyModeDefaultsToClassicTrue();
			editStandDisplayFieldsPersistExactValues();
			savedJsonAssertionsRejectNestedSameNameKeys();
			savedJsonAssertionsRejectBroadcastedOwner();
			savedJsonAssertionsParseScientificNotationAsNumbers();
		}
		finally {
			resetInstance();
		}
	}

	private static void loadKeepsDefaultSettingsWhenFileIsEmpty() throws Exception {
		withSettingsFile("", settingsFile -> {
			ClientModSettings.init(settingsFile.toFile());

			assertStandDisplaySettings(ClientModSettings.getSettingsReadOnly(), 50.0F, 50.0F, true, true, false,
					"empty settings file");
			assertSavedSettingsJson(settingsFile, 50.0F, 50.0F, true, true, false, "empty settings file");
		});
	}

	private static void loadMigratesOfficialLegacyDisplaySettings() throws Exception {
		withSettingsFile("""
				{"standObstructionMode":"OFFICIAL_1_21_1","standOpacity":49,"standOthersOpacity":50}
				""", settingsFile -> {
			ClientModSettings.init(settingsFile.toFile());

			assertStandDisplaySettings(ClientModSettings.getSettingsReadOnly(), 51.0F, 50.0F, false, true, false,
					"official legacy migration");
			ClientModSettings.getInstance().save();
			assertSavedSettingsJson(settingsFile, 51.0F, 50.0F, false, true, false, "official legacy migration");
		});
	}

	private static void loadMigratesClassicLegacyDisplaySettings() throws Exception {
		withSettingsFile("""
				{"standObstructionMode":"CLASSIC_1_16_5","standOpacity":100,"standOthersOpacity":0}
				""", settingsFile -> {
			ClientModSettings.init(settingsFile.toFile());

			assertStandDisplaySettings(ClientModSettings.getSettingsReadOnly(), 0.0F, 100.0F, true, true, false,
					"classic legacy migration");
			ClientModSettings.getInstance().save();
			assertSavedSettingsJson(settingsFile, 0.0F, 100.0F, true, true, false, "classic legacy migration");
		});
	}

	private static void loadClampsOutOfRangeLegacyOpacityMigration() throws Exception {
		withSettingsFile("""
				{"standObstructionMode":"OFFICIAL_1_21_1","standOpacity":-5,"standOthersOpacity":150}
				""", settingsFile -> {
			ClientModSettings.init(settingsFile.toFile());

			assertStandDisplaySettings(ClientModSettings.getSettingsReadOnly(), 100.0F, 0.0F, false, true, false,
					"out-of-range legacy opacity migration");
			ClientModSettings.getInstance().save();
			assertSavedSettingsJson(settingsFile, 100.0F, 0.0F, false, true, false,
					"out-of-range legacy opacity migration");
		});
	}

	private static void loadPrefersNewStandDisplayKeysOverLegacyValues() throws Exception {
		withSettingsFile("""
				{"standObstructionMode":"CLASSIC_1_16_5","standOpacity":49,"standOthersOpacity":50,
				"standTransparency":12,"standOthersTransparency":34,"classicStandObstruction":false,"standOutline":false}
				""", settingsFile -> {
			ClientModSettings.init(settingsFile.toFile());

			assertStandDisplaySettings(ClientModSettings.getSettingsReadOnly(), 12.0F, 34.0F, false, false, false,
					"new keys override legacy");
			ClientModSettings.getInstance().save();
			assertSavedSettingsJson(settingsFile, 12.0F, 34.0F, false, false, false, "new keys override legacy");
		});
	}

	private static void loadInvalidNewStandDisplayKeysDoNotFallBackToLegacyValues() throws Exception {
		withSettingsFile("""
				{"standTransparency":"bad","standOthersTransparency":[],"classicStandObstruction":"bad",
				"standOpacity":49,"standOthersOpacity":50,"standObstructionMode":"OFFICIAL_1_21_1",
				"standOutline":false,"toggleDisableHotbars":true}
				""", settingsFile -> {
			ClientModSettings.init(settingsFile.toFile());

			assertStandDisplaySettings(ClientModSettings.getSettingsReadOnly(), 50.0F, 50.0F, true, false, true,
					"invalid new keys block legacy fallback");
			ClientModSettings.getInstance().save();
			assertSavedSettingsJson(settingsFile, 50.0F, 50.0F, true, false, true, "invalid new keys block legacy fallback");
		});
	}

	private static void loadClampsFiniteTransparencyValues() throws Exception {
		withSettingsFile("""
				{"standTransparency":-5,"standOthersTransparency":150,"classicStandObstruction":true}
				""", settingsFile -> {
			ClientModSettings.init(settingsFile.toFile());

			assertStandDisplaySettings(ClientModSettings.getSettingsReadOnly(), 0.0F, 100.0F, true, true, false,
					"finite clamp");
 			ClientModSettings.getInstance().save();
			assertSavedSettingsJson(settingsFile, 0.0F, 100.0F, true, true, false, "finite clamp");
		});
	}

	private static void loadDefaultsNonFiniteStandDisplayFieldsWithoutTouchingValidFields() throws Exception {
		withSettingsFile("""
				{"standTransparency":1e309,"standOthersTransparency":25,"toggleDisableHotbars":true}
				""", settingsFile -> {
			ClientModSettings.init(settingsFile.toFile());

			assertStandDisplaySettings(ClientModSettings.getSettingsReadOnly(), 50.0F, 25.0F, true, true, true,
					"nonfinite transparency defaults");
			ClientModSettings.getInstance().save();
			assertSavedSettingsJson(settingsFile, 50.0F, 25.0F, true, true, true, "nonfinite transparency defaults");
		});
	}

	private static void loadDefaultsInvalidClassicBooleanWithoutTouchingValidFields() throws Exception {
		withSettingsFile("""
				{"classicStandObstruction":"bad","standTransparency":12,"standOthersTransparency":34,
				"standOutline":false,"toggleDisableHotbars":true}
				""", settingsFile -> {
			ClientModSettings.init(settingsFile.toFile());

			assertStandDisplaySettings(ClientModSettings.getSettingsReadOnly(), 12.0F, 34.0F, true, false, true,
					"invalid classic boolean defaults");
			ClientModSettings.getInstance().save();
			assertSavedSettingsJson(settingsFile, 12.0F, 34.0F, true, false, true, "invalid classic boolean defaults");
		});
	}

	private static void loadInvalidStandDisplayFieldsPreserveUnrelatedSettings() throws Exception {
		withSettingsFile("""
				{"standTransparency":"bad","standOthersTransparency":[],"standOutline":false,"toggleDisableHotbars":true}
				""", settingsFile -> {
			ClientModSettings.init(settingsFile.toFile());

			assertStandDisplaySettings(ClientModSettings.getSettingsReadOnly(), 50.0F, 50.0F, true, false, true,
					"invalid stand display fields preserve unrelated settings");
			ClientModSettings.getInstance().save();
			assertSavedSettingsJson(settingsFile, 50.0F, 50.0F, true, false, true,
					"invalid stand display fields preserve unrelated settings");
		});
	}

	private static void loadUnknownLegacyModeDefaultsToClassicTrue() throws Exception {
		withSettingsFile("""
				{"standObstructionMode":"SURPRISE","standOpacity":49,"toggleDisableHotbars":true}
				""", settingsFile -> {
			ClientModSettings.init(settingsFile.toFile());

			assertStandDisplaySettings(ClientModSettings.getSettingsReadOnly(), 51.0F, 50.0F, true, true, true,
					"unknown legacy mode defaults to classic");
			ClientModSettings.getInstance().save();
			assertSavedSettingsJson(settingsFile, 51.0F, 50.0F, true, true, true,
					"unknown legacy mode defaults to classic");
		});
	}

	private static void editStandDisplayFieldsPersistExactValues() throws Exception {
		withSettingsFile("", settingsFile -> {
			ClientModSettings.init(settingsFile.toFile());

			ClientModSettings.edit(settings -> {
				settings.standTransparency = 33.0F;
				settings.standOthersTransparency = 44.0F;
				settings.classicStandObstruction = false;
				settings.standOutline = false;
			}, false);

			assertStandDisplaySettings(ClientModSettings.getSettingsReadOnly(), 33.0F, 44.0F, false, false, false,
					"new stand display edits persist exact values");
			assertSavedSettingsJson(settingsFile, 33.0F, 44.0F, false, false, false,
					"new stand display edits persist exact values");
		});
	}

	private static void savedJsonAssertionsRejectNestedSameNameKeys() throws Exception {
		String nestedOnlyJson = """
				{
				  "abilitySelectionWheel": true,
				  "broadcasted": {
				    "standTransparency": 33,
				    "standOthersTransparency": 44,
				    "classicStandObstruction": false,
				    "standOutline": false,
				    "toggleDisableHotbars": false
				  }
				}
				""";
		assertThrowsAssertion(() -> assertSavedSettingsJsonText(nestedOnlyJson, 33.0F, 44.0F, false, false, false,
				"nested same-name keys"), "standTransparency");
	}

	private static void savedJsonAssertionsParseScientificNotationAsNumbers() throws Exception {
		String scientificNotationJson = """
				{
				  "abilitySelectionWheel": true,
				  "broadcasted": {},
				  "standTransparency": 3.3e1,
				  "standOthersTransparency": 4.4e1,
				  "classicStandObstruction": false,
				  "standOutline": false,
				  "toggleDisableHotbars": false
				}
				""";
		assertSavedSettingsJsonText(scientificNotationJson, 33.0F, 44.0F, false, false, false,
				"scientific notation parse");
	}

	private static void savedJsonAssertionsRejectBroadcastedOwner() throws Exception {
		String broadcastedOwnerJson = """
				{
				  "abilitySelectionWheel": true,
				  "broadcasted": {
				    "owner": "runtime-only"
				  },
				  "standTransparency": 33,
				  "standOthersTransparency": 44,
				  "classicStandObstruction": false,
				  "standOutline": false,
				  "toggleDisableHotbars": false
				}
				""";
		assertThrowsAssertion(() -> assertSavedSettingsJsonText(broadcastedOwnerJson, 33.0F, 44.0F, false, false, false,
				"broadcasted owner"), "broadcasted.owner");
	}

	private static void assertStandDisplaySettings(ClientModSettings.Settings settings, float expectedTransparency,
			float expectedOthersTransparency, boolean expectedClassic, boolean expectedOutline,
			boolean expectedToggleDisableHotbars, String caseName) {
		if (settings == null) {
			throw new AssertionError("Expected settings for " + caseName + ", got null");
		}
		assertFloatEquals(expectedTransparency, settings.standTransparency, caseName + " own transparency");
		assertFloatEquals(expectedOthersTransparency, settings.standOthersTransparency,
				caseName + " other transparency");
		assertBooleanEquals(expectedClassic, settings.classicStandObstruction, caseName + " classic toggle");
		assertBooleanEquals(expectedOutline, settings.standOutline, caseName + " outline toggle");
		assertBooleanEquals(expectedToggleDisableHotbars, settings.toggleDisableHotbars,
				caseName + " toggleDisableHotbars");
	}

	private static void assertSavedSettingsJson(Path settingsFile, float expectedTransparency,
			float expectedOthersTransparency, boolean expectedClassic, boolean expectedOutline,
			boolean expectedToggleDisableHotbars, String caseName) throws Exception {
		String jsonText = Files.readString(settingsFile);
		assertSavedSettingsJsonText(jsonText, expectedTransparency, expectedOthersTransparency, expectedClassic,
				expectedOutline, expectedToggleDisableHotbars, caseName);
	}

	private static void assertSavedSettingsJsonText(String jsonText, float expectedTransparency,
			float expectedOthersTransparency, boolean expectedClassic, boolean expectedOutline,
			boolean expectedToggleDisableHotbars, String caseName) throws Exception {
		if (jsonText.isBlank()) {
			throw new AssertionError("Expected saved client settings JSON for " + caseName + ", got blank file");
		}
		String trimmed = jsonText.trim();
		if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
			throw new AssertionError("Expected saved client settings object for " + caseName);
		}
		GsonReflectionJsonObject json = GSON.parseTopLevelObject(jsonText, caseName);
		json.assertHasPrimitiveBoolean("abilitySelectionWheel", caseName);
		GsonReflectionJsonObject broadcasted = json.requireObject("broadcasted", caseName);
		broadcasted.assertMissing("owner", caseName, "broadcasted.owner");
		json.assertMissing("standObstructionMode", caseName);
		json.assertMissing("standOpacity", caseName);
		json.assertMissing("standOthersOpacity", caseName);
		json.assertPrimitiveFloatEquals("standTransparency", expectedTransparency, caseName);
		json.assertPrimitiveFloatEquals("standOthersTransparency", expectedOthersTransparency, caseName);
		json.assertPrimitiveBooleanEquals("classicStandObstruction", expectedClassic, caseName);
		json.assertPrimitiveBooleanEquals("standOutline", expectedOutline, caseName);
		json.assertPrimitiveBooleanEquals("toggleDisableHotbars", expectedToggleDisableHotbars, caseName);
	}

	private static void assertFloatEquals(float expected, float actual, String caseName) {
		if (Float.compare(expected, actual) != 0) {
			throw new AssertionError("Expected " + caseName + " to be " + expected + ", got " + actual);
		}
	}

	private static void assertBooleanEquals(boolean expected, boolean actual, String caseName) {
		if (expected != actual) {
			throw new AssertionError("Expected " + caseName + " to be " + expected + ", got " + actual);
		}
	}

	private static void assertThrowsAssertion(ThrowingRunnable runnable, String expectedMessagePart) throws Exception {
		try {
			runnable.run();
		}
		catch (AssertionError error) {
			if (error.getMessage() == null || !error.getMessage().contains(expectedMessagePart)) {
				throw new AssertionError("Expected assertion containing '" + expectedMessagePart + "', got: "
						+ error.getMessage(), error);
			}
			return;
		}
		throw new AssertionError("Expected AssertionError containing '" + expectedMessagePart + "'");
	}

	private static void withSettingsFile(String initialJson, ThrowingPathConsumer consumer) throws Exception {
		Path settingsFile = Files.createTempFile("client_settings", ".json");
		try {
			Files.writeString(settingsFile, initialJson);
			consumer.accept(settingsFile);
		}
		finally {
			resetInstance();
			Files.deleteIfExists(settingsFile);
		}
	}

	private static void resetInstance() throws Exception {
		Field instance = ClientModSettings.class.getDeclaredField("instance");
		instance.setAccessible(true);
		instance.set(null, null);
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}

	@FunctionalInterface
	private interface ThrowingPathConsumer {
		void accept(Path path) throws Exception;
	}

	private static final class GsonReflectionJson {
		private final Method parseString;
		private final Method isJsonObject;
		private final Method getAsJsonObject;
		private final Method getObjectMember;
		private final Method hasObjectMember;
		private final Method isJsonPrimitive;
		private final Method getAsJsonPrimitive;
		private final Method primitiveIsBoolean;
		private final Method primitiveIsNumber;
		private final Method getAsBoolean;
		private final Method getAsDouble;

		private GsonReflectionJson() {
			try {
				Class<?> parserClass = Class.forName("com.google.gson.JsonParser");
				Class<?> elementClass = Class.forName("com.google.gson.JsonElement");
				Class<?> objectClass = Class.forName("com.google.gson.JsonObject");
				Class<?> primitiveClass = Class.forName("com.google.gson.JsonPrimitive");
				parseString = parserClass.getMethod("parseString", String.class);
				isJsonObject = elementClass.getMethod("isJsonObject");
				getAsJsonObject = elementClass.getMethod("getAsJsonObject");
				getObjectMember = objectClass.getMethod("get", String.class);
				hasObjectMember = objectClass.getMethod("has", String.class);
				isJsonPrimitive = elementClass.getMethod("isJsonPrimitive");
				getAsJsonPrimitive = elementClass.getMethod("getAsJsonPrimitive");
				primitiveIsBoolean = primitiveClass.getMethod("isBoolean");
				primitiveIsNumber = primitiveClass.getMethod("isNumber");
				getAsBoolean = elementClass.getMethod("getAsBoolean");
				getAsDouble = elementClass.getMethod("getAsDouble");
			}
			catch (ReflectiveOperationException exception) {
				throw new RuntimeException("Failed to initialize Gson reflection adapter", exception);
			}
		}

		private GsonReflectionJsonObject parseTopLevelObject(String jsonText, String caseName) throws Exception {
			Object element = parseString.invoke(null, jsonText);
			if (!((Boolean) isJsonObject.invoke(element)).booleanValue()) {
				throw new AssertionError("Expected saved client settings object for " + caseName);
			}
			return new GsonReflectionJsonObject(getAsJsonObject.invoke(element), this);
		}
	}

	private static final class GsonReflectionJsonObject {
		private final Object jsonObject;
		private final GsonReflectionJson gson;

		private GsonReflectionJsonObject(Object jsonObject, GsonReflectionJson gson) {
			this.jsonObject = jsonObject;
			this.gson = gson;
		}

		private GsonReflectionJsonObject requireObject(String key, String caseName) throws Exception {
			Object member = requireMember(key, caseName);
			if (!((Boolean) gson.isJsonObject.invoke(member)).booleanValue()) {
				throw new AssertionError("Expected saved client settings " + key + " to be a top-level object for "
						+ caseName);
			}
			return new GsonReflectionJsonObject(member, gson);
		}

		private void assertHasPrimitiveBoolean(String key, String caseName) throws Exception {
			Object member = requireMember(key, caseName);
			Object primitive = requirePrimitive(member, key, caseName);
			if (!((Boolean) gson.primitiveIsBoolean.invoke(primitive)).booleanValue()) {
				throw new AssertionError("Expected saved client settings " + key + " to be a top-level boolean for "
						+ caseName);
			}
		}

		private void assertPrimitiveBooleanEquals(String key, boolean expected, String caseName) throws Exception {
			Object member = requireMember(key, caseName);
			Object primitive = requirePrimitive(member, key, caseName);
			if (!((Boolean) gson.primitiveIsBoolean.invoke(primitive)).booleanValue()) {
				throw new AssertionError("Expected saved client settings " + key + " to be a top-level boolean for "
						+ caseName);
			}
			boolean actual = ((Boolean) gson.getAsBoolean.invoke(member)).booleanValue();
			assertBooleanEquals(expected, actual, caseName + " saved " + key);
		}

		private void assertPrimitiveFloatEquals(String key, float expected, String caseName) throws Exception {
			Object member = requireMember(key, caseName);
			Object primitive = requirePrimitive(member, key, caseName);
			if (!((Boolean) gson.primitiveIsNumber.invoke(primitive)).booleanValue()) {
				throw new AssertionError("Expected saved client settings " + key + " to be a top-level number for "
						+ caseName);
			}
			double actual = ((Double) gson.getAsDouble.invoke(member)).doubleValue();
			assertFloatEquals(expected, (float) actual, caseName + " saved " + key);
		}

		private void assertMissing(String key, String caseName) throws Exception {
			assertMissing(key, caseName, key);
		}

		private void assertMissing(String key, String caseName, String displayKey) throws Exception {
			if (((Boolean) gson.hasObjectMember.invoke(jsonObject, key)).booleanValue()) {
				throw new AssertionError("Expected saved client settings to exclude " + displayKey + " for "
						+ caseName);
			}
		}

		private Object requireMember(String key, String caseName) throws Exception {
			if (!((Boolean) gson.hasObjectMember.invoke(jsonObject, key)).booleanValue()) {
				throw new AssertionError("Expected saved client settings to include top-level " + key + " for "
						+ caseName);
			}
			Object member = gson.getObjectMember.invoke(jsonObject, key);
			if (member == null) {
				throw new AssertionError("Expected saved client settings to include non-null top-level " + key + " for "
						+ caseName);
			}
			return member;
		}

		private Object requirePrimitive(Object member, String key, String caseName) throws Exception {
			if (!((Boolean) gson.isJsonPrimitive.invoke(member)).booleanValue()) {
				throw new AssertionError("Expected saved client settings " + key + " to be a top-level primitive for "
						+ caseName);
			}
			return gson.getAsJsonPrimitive.invoke(member);
		}
	}
}
