package com.github.standobyte.jojoimpl.stands.starplatinum;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class StarFingerVisualOwnershipSmokeTest {
	private static final Set<String> PRODUCTION_BONES = Set.of(
			"body_rot", "body", "torso", "torso_bend", "head", "head_rot",
			"left_arm", "left_arm_bend", "right_arm", "right_arm_bend",
			"left_leg", "left_leg_bend", "right_leg", "right_leg_bend");
	private static final Set<String> LEGACY_BONES = Set.of(
			"body", "left_arm_xrot", "left_arm_bend", "right_arm_xrot",
			"right_arm_bend", "left_leg_xrot", "left_leg_bend",
			"right_leg_xrot", "right_leg_bend", "front_fabric",
			"back_fabric", "torso_bend", "root", "head", "left_arm",
			"right_arm", "left_leg", "right_leg");

	private StarFingerVisualOwnershipSmokeTest() {}

	public static void main(String[] args) {
		run();
	}

	public static void run() {
		Path root = Path.of(System.getProperty("user.dir"));
		Path animationPath = root.resolve(
				"src/main/resources/assets/jojo_ripples/stand_skins/"
				+ "star_platinum/assets/jojo_ripples/animations/"
				+ "star_platinum.animation.json");
		JsonObject animations = requireObject(
				readObject(animationPath), "animations");

		JsonObject production = requireObject(animations, "star_finger");
		JsonObject productionBones = requireObject(production, "bones");
		check(!productionBones.has("hidden.finger"),
				"star_finger must not animate the legacy model extension");
		check(PRODUCTION_BONES.equals(productionBones.keySet()),
				"star_finger pose channels changed unexpectedly");
		check(production.has("animation_length")
				&& production.get("animation_length").getAsFloat() == 1.3F,
				"star_finger animation length changed");

		JsonObject timeline = requireObject(production, "timeline");
		check(timeline.keySet().equals(Set.of("0.0", "0.125", "0.75"))
				&& "phase = WINDUP;".equals(
						timeline.get("0.0").getAsString())
				&& "phase = PERFORM;".equals(
						timeline.get("0.125").getAsString())
				&& "phase = RECOVERY;".equals(
						timeline.get("0.75").getAsString()),
				"star_finger phase timeline changed");

		JsonObject legacy = requireObject(animations, "starFinger");
		JsonObject legacyBones = requireObject(legacy, "bones");
		check(legacy.keySet().equals(Set.of("loop", "bones"))
				&& "hold_on_last_frame".equals(
						legacy.get("loop").getAsString())
				&& LEGACY_BONES.equals(legacyBones.keySet())
				&& !legacyBones.has("hidden.finger"),
				"legacy starFinger compatibility animation changed");

		String ability = read(root.resolve(
				"src/main/java/com/github/standobyte/jojoimpl/stands/"
				+ "starplatinum/StarFingerAbility.java"));
		check(ability.contains(
				"ActionAnimIdentifier.getOrCreate(\"star_finger\", false)"),
				"Star Finger no longer uses the production animation");
		check(ability.contains("new SPStarFingerEntity(stand, level())")
				&& ability.contains("addProjectileWithStandStats(starFinger)"),
				"dedicated Star Finger visual entity spawn is missing");

		String renderers = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/client/"
				+ "ModEntityTypeRenderers.java"));
		check(renderers.contains(
				"event.registerEntityRenderer(ModEntityTypes.SP_STAR_FINGER.get(), "
				+ "SPStarFingerRenderer::new)"),
				"dedicated Star Finger renderer registration is missing");
	}

	private static JsonObject readObject(Path path) {
		try (Reader reader = Files.newBufferedReader(
				path, StandardCharsets.UTF_8)) {
			JsonElement json = JsonParser.parseReader(reader);
			check(json.isJsonObject(),
					"resource is not a JSON object: " + path);
			return json.getAsJsonObject();
		}
		catch (IOException exception) {
			throw new AssertionError("failed to read " + path, exception);
		}
	}

	private static JsonObject requireObject(JsonObject parent, String key) {
		JsonElement element = parent.get(key);
		check(element != null && element.isJsonObject(),
				"missing JSON object: " + key);
		return element.getAsJsonObject();
	}

	private static String read(Path path) {
		try {
			return Files.readString(path, StandardCharsets.UTF_8);
		}
		catch (IOException exception) {
			throw new AssertionError("failed to read " + path, exception);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
